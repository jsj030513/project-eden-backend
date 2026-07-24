package com.projecteden.dataset;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Filesystem-safe validation mirror of the currently seeded taxonomy codes. */
final class GroundTruthTaxonomyValidator {
	private static final Set<String> CATEGORY_CODES = Set.of("NATURE", "ANIMAL", "FOOD", "WATER", "WALK", "STUDY", "WORK", "PEOPLE", "FAMILY", "FRIENDS", "DAILY_LIFE", "EXERCISE", "TRAVEL", "CULTURE", "EXHIBITION", "MUSIC", "MOVIE", "SHOPPING", "REST", "EMOTION", "PLACE");
	private static final Set<String> TAG_CODES = Set.of("CAT", "DOG", "PERSON", "FLOWER", "TREE", "FOOD", "BOOK", "COMPUTER", "INDOOR", "OUTDOOR", "PARK", "HOME", "CAFE", "OFFICE", "SCHOOL", "WALKING", "STUDYING", "WORKING", "EATING", "RESTING", "TRAVELING", "WARM", "CALM", "JOYFUL", "QUIET", "ENERGETIC", "ALONE", "FRIENDS", "FAMILY", "BENCH", "BRIDGE", "TABLE", "ROAD", "WATER");

	void validatePatch(GroundTruthPatch patch) {
		validateDistinct(patch.secondaryCategories(), "DUPLICATE_SECONDARY_CATEGORY");
		validateDistinct(patch.tags(), "DUPLICATE_TAG");
		validateDistinct(patch.objects(), "DUPLICATE_OBJECT");
		validateDistinct(patch.activities(), "DUPLICATE_ACTIVITY");
		validateDistinct(patch.relationships(), "DUPLICATE_RELATIONSHIP");
	}

	void validateGroundTruth(VisionGroundTruth groundTruth) {
		validateCategory(groundTruth.category());
		groundTruth.secondaryCategories().forEach(this::validateCategory);
		groundTruth.tags().forEach(this::validateTag);
	}

	private void validateCategory(String code) {
		if (code != null && !CATEGORY_CODES.contains(code)) throw new IllegalArgumentException("UNKNOWN_TAXONOMY_CATEGORY");
	}

	private void validateTag(String code) {
		if (!TAG_CODES.contains(code)) throw new IllegalArgumentException("UNKNOWN_TAXONOMY_TAG");
	}

	private void validateDistinct(List<String> codes, String error) {
		if (codes == null) return;
		Set<String> values = new HashSet<>();
		for (String code : codes) {
			if (code == null || code.isBlank() || !values.add(code.trim().toUpperCase())) throw new IllegalArgumentException(error);
		}
	}
}
