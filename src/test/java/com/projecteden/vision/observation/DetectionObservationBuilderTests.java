package com.projecteden.vision.observation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.projecteden.vision.detection.BoundingBox;
import com.projecteden.vision.detection.DetectionConfidence;
import com.projecteden.vision.detection.DetectionObject;
import com.projecteden.vision.detection.DetectionResult;

class DetectionObservationBuilderTests {
	private final DetectionObservationBuilder builder = new DetectionObservationBuilder(new DetectionSceneResolver(), new DetectionSubjectResolver(), new DetectionObjectResolver());

	@Test void emptyDetectionBecomesTruthfulGeneralMemory() { var observation=builder.build(result()); assertThat(observation.recognized()).isTrue(); assertThat(observation.fallback()).isTrue(); assertThat(observation.objects()).containsExactly("OBJECT"); }
	@Test void personBecomesSubject() { var observation=builder.build(result("PERSON")); assertThat(observation.subjects()).containsExactly("PERSON"); }
	@Test void catIsRetainedAsObject() { var observation=builder.build(result("CAT")); assertThat(observation.objects()).containsExactly("CAT"); assertThat(observation.confidence()).isEqualByComparingTo("0.8"); }
	@Test void personAndCatAddsEvidenceBasedRelationships() { var observation=builder.build(result("PERSON","CAT")); assertThat(observation.relationships()).contains("PERSON_WITH_CAT", "PERSON_WITH_ANIMAL"); }
	@Test void bookAndLaptopWithoutPersonDoNotCreateAnActivity() { assertThat(builder.build(result("BOOK","LAPTOP")).activities()).isEmpty(); }
	@Test void unmappedObjectUsesExplicitBroadFallback() { var observation=builder.build(result("CUSTOM_OBJECT")); assertThat(observation.objects()).containsExactly("UNKNOWN_OBJECT"); assertThat(observation.recognized()).isTrue(); }

	private DetectionResult result(String... codes) { return new DetectionResult(java.util.Arrays.stream(codes).map(code -> new DetectionObject(code,new DetectionConfidence(.8f),new BoundingBox(0,0,10,10))).toList(),"yolox-nano-0.1.1rc0"); }
}
