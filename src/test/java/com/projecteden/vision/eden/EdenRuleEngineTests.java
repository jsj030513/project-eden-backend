package com.projecteden.vision.eden;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.projecteden.vision.detection.BoundingBox;
import com.projecteden.vision.detection.DetectionConfidence;
import com.projecteden.vision.detection.DetectionObject;
import com.projecteden.vision.detection.DetectionResult;

class EdenRuleEngineTests {
	private final CocoToEdenObjectMapper mapper = new CocoToEdenObjectMapper();
	private final EdenActivityRuleEngine activities = new EdenActivityRuleEngine();
	private final EdenRelationshipRuleEngine relationships = new EdenRelationshipRuleEngine();

	@Test void createsReadingOnlyWhenPersonAndBookAreNear() { assertThat(activity("PERSON", "BOOK")).extracting(EdenRuleCandidate::code).contains("READING"); }
	@Test void rejectsBookLaptopDogBicycleAndCupOnlyFalsePositives() {
		assertThat(activity("BOOK")).isEmpty(); assertThat(activity("LAPTOP")).isEmpty(); assertThat(activity("DOG")).isEmpty(); assertThat(activity("BICYCLE")).isEmpty(); assertThat(activity("CUP")).isEmpty();
	}
	@Test void createsRelationshipsOnlyForNearbyObjects() {
		assertThat(relationship("PERSON", "CAT")).extracting(EdenRuleCandidate::code).contains("PERSON_WITH_CAT", "PERSON_WITH_ANIMAL");
		assertThat(relationshipFar("PERSON", "CAT")).isEmpty();
	}
	@Test void acceptsWorkCyclingEatingAndMultiplePersonsWithRequiredEvidence() {
		assertThat(activity("PERSON", "LAPTOP")).extracting(EdenRuleCandidate::code).contains("WORK_OR_STUDY");
		assertThat(activity("PERSON", "BICYCLE")).extracting(EdenRuleCandidate::code).contains("CYCLING");
		assertThat(activity("PERSON", "CUP", "TABLE")).extracting(EdenRuleCandidate::code).contains("EATING_OR_CAFE");
		assertThat(relationship("PERSON", "PERSON")).extracting(EdenRuleCandidate::code).contains("MULTIPLE_PERSONS");
	}
	private List<EdenRuleCandidate> activity(String... codes) { return activities.evaluate(facts(codes, false), .65f); }
	private List<EdenRuleCandidate> relationship(String... codes) { return relationships.evaluate(facts(codes, false), .55f); }
	private List<EdenRuleCandidate> relationshipFar(String... codes) { return relationships.evaluate(facts(codes, true), .55f); }
	private EdenObservationFacts facts(String[] codes, boolean far) {
		var objects = java.util.stream.IntStream.range(0, codes.length).mapToObj(index -> {
			float offset = index * 2f;
			return new DetectionObject(codes[index], new DetectionConfidence(.9f), far && index == 1 ? new BoundingBox(100,100,110,110) : new BoundingBox(offset,offset,10+offset,10+offset));
		}).toList();
		return new EdenObservationFacts(mapper.map(new DetectionResult(objects, "test")), .25f);
	}
}
