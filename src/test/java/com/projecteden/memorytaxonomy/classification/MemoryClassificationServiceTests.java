package com.projecteden.memorytaxonomy.classification;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.projecteden.memorytaxonomy.observation.ImageObservation;
import com.projecteden.memorytaxonomy.repository.MemoryTaxonomyCategoryRepository;

@SpringBootTest
@ActiveProfiles("test")
class MemoryClassificationServiceTests {

	@Autowired private MemoryClassificationService service;
	@Autowired private MemoryTaxonomyCategoryRepository categories;

	@Test
	void classifiesPrimaryCategories() {
		assertThat(classify(subject("CAT")).primaryCategory()).isEqualTo("ANIMAL");
		assertThat(classify(object("FLOWER")).primaryCategory()).isEqualTo("NATURE");
		assertThat(classify(object("FOOD")).primaryCategory()).isEqualTo("FOOD");
		assertThat(classify(object("WATER")).primaryCategory()).isEqualTo("WATER");
		assertThat(classify(activity("WALKING")).primaryCategory()).isEqualTo("WALK");
		assertThat(classify(activity("STUDYING")).primaryCategory()).isEqualTo("STUDY");
		assertThat(classify(activity("WORKING")).primaryCategory()).isEqualTo("WORK");
	}

	@Test
	void classifiesSecondaryCategoriesWithStableOrderAndNoDuplicatePrimary() {
		ImageObservation observation = ImageObservation.recognized(
				List.of("CAT"),
				List.of("FLOWER", "WATER"),
				null,
				List.of("WALKING"),
				List.of(),
				List.of(),
				"MOCK",
				"mock-v1",
				BigDecimal.valueOf(0.82));

		MemoryClassificationResult result = service.classify(observation);

		assertThat(result.primaryCategory()).isEqualTo("ANIMAL");
		assertThat(result.secondaryCategories()).containsExactly("NATURE", "WATER", "WALK");
	}

	@Test
	void classifiesOnlyCertainTagsFromObservation() {
		MemoryClassificationResult result = classify(ImageObservation.recognized(
				List.of("CAT"),
				List.of("FLOWER", "TOMATO"),
				"PARK",
				List.of("STUDYING"),
				List.of("FRIEND"),
				List.of("WARM"),
				"MOCK",
				"mock-v1",
				BigDecimal.valueOf(0.82)));

		assertThat(result.tags()).containsExactly("CAT", "FLOWER", "FOOD", "STUDYING", "PARK");
		assertThat(result.tags()).doesNotContain("FRIEND", "WARM");
	}

	@Test
	void unknownFallbackHasNoPrimarySecondaryOrTags() {
		MemoryClassificationResult result =
				service.classify(ImageObservation.fallback("MOCK", "mock-v1"));

		assertThat(result.primaryCategory()).isNull();
		assertThat(result.secondaryCategories()).isEmpty();
		assertThat(result.tags()).isEmpty();
		assertThat(result.fallback()).isTrue();
	}

	@Test
	void inactivePrimaryCategoryFallsBack() {
		var category = categories.findByCode("WALK").orElseThrow();
		try {
			ReflectionTestUtils.setField(category, "active", false);
			categories.saveAndFlush(category);

			MemoryClassificationResult result = classify(activity("WALKING"));

			assertThat(result.primaryCategory()).isNull();
			assertThat(result.fallback()).isTrue();
		} finally {
			ReflectionTestUtils.setField(category, "active", true);
			categories.saveAndFlush(category);
		}
	}

	private MemoryClassificationResult classify(ImageObservation observation) {
		return service.classify(observation);
	}

	private ImageObservation subject(String subject) {
		return ImageObservation.recognized(
				List.of(subject), List.of(), null, List.of(), List.of(), List.of(),
				"MOCK", "mock-v1", BigDecimal.valueOf(0.82));
	}

	private ImageObservation object(String object) {
		return ImageObservation.recognized(
				List.of(), List.of(object), null, List.of(), List.of(), List.of(),
				"MOCK", "mock-v1", BigDecimal.valueOf(0.82));
	}

	private ImageObservation activity(String activity) {
		return ImageObservation.recognized(
				List.of(), List.of(), null, List.of(activity), List.of(), List.of(),
				"MOCK", "mock-v1", BigDecimal.valueOf(0.82));
	}
}
