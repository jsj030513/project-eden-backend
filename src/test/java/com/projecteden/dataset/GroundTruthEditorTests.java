package com.projecteden.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class GroundTruthEditorTests {

	private final GroundTruthEditor editor = new GroundTruthEditor();

	@Test
	void patchesOnlySpecifiedFieldsAndPreservesPrediction() {
		VisionGroundTruth prediction = truth("ANIMAL", List.of(), List.of("CAT"));
		ReviewItem review = approved(prediction);

		GroundTruthEditResult result = editor.edit(review,
				new GroundTruthPatch("FOOD", List.of("DAILY_LIFE"), List.of("FOOD"), null, List.of(), null, false, "editor note"),
				"reviewer-b");

		assertThat(result.groundTruth().category()).isEqualTo("FOOD");
		assertThat(result.groundTruth().secondaryCategories()).containsExactly("DAILY_LIFE");
		assertThat(result.groundTruth().tags()).containsExactly("FOOD");
		assertThat(result.groundTruth().activities()).isEmpty();
		assertThat(result.groundTruth().objects()).containsExactly("CAT");
		assertThat(review.prediction()).isEqualTo(prediction);
		assertThat(result.notes()).isEqualTo("editor note");
	}

	@Test
	void rejectsDuplicateAndInvalidTaxonomyAndFallbackSignals() {
		ReviewItem review = approved(truth("ANIMAL", List.of(), List.of("CAT")));
		assertThatThrownBy(() -> editor.edit(review, new GroundTruthPatch(null, null, List.of("CAT", "cat"), null, null, null, null, null), null))
				.hasMessageContaining("DUPLICATE_TAG");
		assertThatThrownBy(() -> editor.edit(review, new GroundTruthPatch("NOT_A_CATEGORY", null, null, null, null, null, null, null), null))
				.hasMessageContaining("UNKNOWN_TAXONOMY_CATEGORY");
		assertThatThrownBy(() -> editor.edit(review, new GroundTruthPatch(null, null, null, null, null, null, true, null), null))
				.hasMessageContaining("Fallback ground truth cannot contain signals");
	}

	@Test
	void forbidsEditingRejectedReview() {
		ReviewItem rejected = new ReviewItem(null, "review-0001", new VisionDatasetId("eden-local"), new VisionDatasetCaseId("cat-001"),
				Instant.EPOCH, Instant.EPOCH, ReviewStatus.REJECTED, truth("ANIMAL", List.of(), List.of("CAT")), null, null, null);
		assertThatThrownBy(() -> editor.edit(rejected, new GroundTruthPatch(null, null, null, null, null, null, null, null), null))
				.hasMessageContaining("GROUND_TRUTH_EDIT_NOT_ALLOWED");
	}

	private ReviewItem approved(VisionGroundTruth prediction) {
		return new ReviewItem(null, "review-0001", new VisionDatasetId("eden-local"), new VisionDatasetCaseId("cat-001"),
				Instant.EPOCH, Instant.EPOCH, ReviewStatus.APPROVED, prediction, prediction, null, null);
	}

	private VisionGroundTruth truth(String category, List<String> secondary, List<String> tags) {
		return new VisionGroundTruth(List.of("CAT"), List.of("WALKING"), List.of("FRIENDS"), category, secondary, tags, false);
	}
}
