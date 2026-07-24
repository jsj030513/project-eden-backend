package com.projecteden.memorytaxonomy.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.village.domain.VillageCategory;

class LegacyVillageCategoryMapperTests {

	private final LegacyVillageCategoryMapper mapper = new LegacyVillageCategoryMapper();

	@Test
	void allRecognizedObjectsAreExplicitlyHandledThroughTheirLegacyCategory() {
		for (RecognizedObject object : RecognizedObject.values()) {
			if (object == RecognizedObject.UNKNOWN
					|| object.getCategory() == VillageCategory.UNKNOWN) {
				assertThat(mapper.toTaxonomyCategoryCode(object)).isEmpty();
				assertThat(mapper.shouldFallback(object != RecognizedObject.UNKNOWN, object)).isTrue();
			} else {
				assertThat(mapper.toTaxonomyCategoryCode(object))
						.contains(object.getCategory().name());
				assertThat(mapper.shouldFallback(true, object)).isFalse();
			}
		}
	}

	@Test
	void allLegacyVillageCategoriesExceptUnknownMapToSameTaxonomyCode() {
		for (VillageCategory category : VillageCategory.values()) {
			if (category == VillageCategory.UNKNOWN) {
				assertThat(mapper.toTaxonomyCategoryCode(category)).isEmpty();
			} else {
				assertThat(mapper.toTaxonomyCategoryCode(category)).contains(category.name());
			}
		}
	}

	@Test
	void requiredCategoriesMapToTaxonomyCodes() {
		assertThat(mapper.toTaxonomyCategoryCode(VillageCategory.NATURE)).contains("NATURE");
		assertThat(mapper.toTaxonomyCategoryCode(VillageCategory.ANIMAL)).contains("ANIMAL");
		assertThat(mapper.toTaxonomyCategoryCode(VillageCategory.FOOD)).contains("FOOD");
		assertThat(mapper.toTaxonomyCategoryCode(VillageCategory.WATER)).contains("WATER");
		assertThat(mapper.toTaxonomyCategoryCode(VillageCategory.WALK)).contains("WALK");
		assertThat(mapper.toTaxonomyCategoryCode(VillageCategory.STUDY)).contains("STUDY");
		assertThat(mapper.toTaxonomyCategoryCode(VillageCategory.WORK)).contains("WORK");
	}

	@Test
	void unknownAndUnrecognizedFallback() {
		assertThat(mapper.toTaxonomyCategoryCode(RecognizedObject.UNKNOWN)).isEmpty();
		assertThat(mapper.toTaxonomyCategoryCode((RecognizedObject) null)).isEmpty();
		assertThat(mapper.shouldFallback(false, RecognizedObject.FLOWER)).isTrue();
		assertThat(mapper.shouldFallback(true, RecognizedObject.UNKNOWN)).isTrue();
	}
}
