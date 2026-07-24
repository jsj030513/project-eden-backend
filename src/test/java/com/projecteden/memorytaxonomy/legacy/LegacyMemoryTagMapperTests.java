package com.projecteden.memorytaxonomy.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.projecteden.ai.domain.RecognizedObject;

class LegacyMemoryTagMapperTests {

	private final LegacyMemoryTagMapper mapper = new LegacyMemoryTagMapper();

	@Test
	void mapsCertainLegacyObjectsToSeededTagCodesOnly() {
		assertThat(mapper.toTagCodes(RecognizedObject.CAT)).containsExactly("CAT");
		assertThat(mapper.toTagCodes(RecognizedObject.DOG)).containsExactly("DOG");
		assertThat(mapper.toTagCodes(RecognizedObject.FLOWER)).containsExactly("FLOWER");
		assertThat(mapper.toTagCodes(RecognizedObject.STUDY)).containsExactly("BOOK", "STUDYING");
		assertThat(mapper.toTagCodes(RecognizedObject.CODING)).containsExactly("COMPUTER", "WORKING");
		assertThat(mapper.toTagCodes(RecognizedObject.ROAD)).containsExactly("ROAD", "WALKING");
		assertThat(mapper.toTagCodes(RecognizedObject.WATER)).containsExactly("WATER");
		assertThat(mapper.toTagCodes(RecognizedObject.FOOD)).containsExactly("FOOD");
	}

	@Test
	void unknownAndLegacyFallbackObjectsReturnNoTags() {
		assertThat(mapper.toTagCodes(RecognizedObject.UNKNOWN)).isEmpty();
		assertThat(mapper.toTagCodes(RecognizedObject.COFFEE)).isEmpty();
		assertThat(mapper.toTagCodes(RecognizedObject.FRIEND)).isEmpty();
		assertThat(mapper.toTagCodes(null)).isEmpty();
	}

	@Test
	void tagCodesDoNotContainDuplicates() {
		for (RecognizedObject object : RecognizedObject.values()) {
			List<String> tagCodes = mapper.toTagCodes(object);
			assertThat(new HashSet<>(tagCodes)).hasSameSizeAs(tagCodes);
		}
	}

	@Test
	void mapperDoesNotInferMoodOrRelationshipTags() {
		for (RecognizedObject object : RecognizedObject.values()) {
			assertThat(mapper.toTagCodes(object))
					.doesNotContain("WARM", "CALM", "FRIENDS", "FAMILY", "INDOOR", "OUTDOOR");
		}
	}
}
