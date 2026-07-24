package com.projecteden.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecteden.imagenormalization.ImageFormat;
import com.projecteden.imagenormalization.ImageNormalizationService;
import com.projecteden.imagenormalization.NormalizedImage;

class FilesystemReviewQueueManagerTests {

	@TempDir
	Path root;

	@Test
	void enqueuesFindsListsAndAppliesDefinedTransitions() throws Exception {
		FilesystemVisionDatasetManager datasetManager = datasetManager();
		VisionDatasetId datasetId = new VisionDatasetId("eden-local");
		datasetManager.createDataset(new VisionDataset(null, datasetId, "Eden", 1, "ACTIVE", 0));
		importCase(datasetManager, datasetId, "cat-001");
		FilesystemReviewQueueManager queue = new FilesystemReviewQueueManager(root);
		VisionGroundTruth prediction = truth("ANIMAL", "CAT");

		ReviewItem pending = queue.enqueue(datasetId, new VisionDatasetCaseId("cat-001"), prediction, "reviewer-a", "initial");
		assertThat(pending.status()).isEqualTo(ReviewStatus.PENDING);
		assertThat(queue.find(datasetId, pending.reviewId())).contains(pending);
		assertThat(queue.listPending(datasetId)).containsExactly(pending);
		assertThatThrownBy(() -> queue.enqueue(datasetId, new VisionDatasetCaseId("cat-001"), prediction, null, null))
				.hasMessageContaining("REVIEW_ALREADY_EXISTS_FOR_CASE");

		ReviewItem approved = queue.approve(datasetId, pending.reviewId(), "reviewer-b", "approved");
		assertThat(approved.status()).isEqualTo(ReviewStatus.APPROVED);
		assertThat(approved.groundTruth()).isEqualTo(prediction);
		assertThat(approved.history()).singleElement().extracting(GroundTruthEditResult::decision).isEqualTo(ReviewDecision.APPROVE);
		assertThat(queue.approve(datasetId, pending.reviewId(), "ignored", "ignored")).isEqualTo(approved);
		assertThatThrownBy(() -> queue.reject(datasetId, pending.reviewId(), null, null))
				.hasMessageContaining("INVALID_REVIEW_TRANSITION");
	}

	@Test
	void correctPreservesPredictionAndRejectDoesNotCreateGroundTruth() throws Exception {
		FilesystemVisionDatasetManager datasetManager = datasetManager();
		VisionDatasetId datasetId = new VisionDatasetId("eden-local");
		datasetManager.createDataset(new VisionDataset(null, datasetId, "Eden", 1, "ACTIVE", 0));
		importCase(datasetManager, datasetId, "cat-001");
		importCase(datasetManager, datasetId, "cat-002");
		FilesystemReviewQueueManager queue = new FilesystemReviewQueueManager(root);
		VisionGroundTruth prediction = truth("ANIMAL", "CAT");

		ReviewItem pendingCorrection = queue.enqueue(datasetId, new VisionDatasetCaseId("cat-001"), prediction, null, null);
		queue.approve(datasetId, pendingCorrection.reviewId(), "reviewer", "approved");
		ReviewItem corrected = queue.correct(datasetId, pendingCorrection.reviewId(),
				truth("FOOD", "FOOD"), "reviewer", "corrected");
		assertThat(corrected.status()).isEqualTo(ReviewStatus.CORRECTED);
		assertThat(corrected.prediction()).isEqualTo(prediction);
		assertThat(corrected.groundTruth()).isEqualTo(truth("FOOD", "FOOD"));
		assertThat(corrected.history()).hasSize(2).extracting(GroundTruthEditResult::decision)
				.containsExactly(ReviewDecision.APPROVE, ReviewDecision.CORRECT);
		ReviewItem edited = queue.editGroundTruth(datasetId, corrected.reviewId(),
				new GroundTruthPatch(null, List.of("DAILY_LIFE"), null, null, null, null, null, "secondary added"), "reviewer-c");
		assertThat(edited.history()).hasSize(3);
		assertThat(queue.find(datasetId, edited.reviewId()).orElseThrow().groundTruth().secondaryCategories())
				.containsExactly("DAILY_LIFE");

		ReviewItem rejected = queue.reject(datasetId,
				queue.enqueue(datasetId, new VisionDatasetCaseId("cat-002"), prediction, null, null).reviewId(), "reviewer", "rejected");
		assertThat(rejected.status()).isEqualTo(ReviewStatus.REJECTED);
		assertThat(rejected.groundTruth()).isNull();
		assertThat(queue.reject(datasetId, rejected.reviewId(), "ignored", "ignored")).isEqualTo(rejected);
	}

	private FilesystemVisionDatasetManager datasetManager() {
		ImageNormalizationService normalization = input -> new NormalizedImage(input.bytes(), "image/jpeg", ImageFormat.JPEG, 2, 2,
				ImageFormat.JPEG, 2, 2, false, false, false, false, false, true,
				Integer.toHexString(java.util.Arrays.hashCode(input.bytes())));
		return new FilesystemVisionDatasetManager(normalization, new ObjectMapper(), root);
	}

	private void importCase(FilesystemVisionDatasetManager manager, VisionDatasetId datasetId, String caseId) throws Exception {
		Path source = root.resolve(caseId + ".jpg");
		Files.writeString(source, caseId);
		manager.importCase(datasetId, new VisionDatasetCase(null, new VisionDatasetCaseId(caseId), datasetId, null, null, 0, 0,
				null, new VisionConsentMetadata(true, true, false, false, Instant.EPOCH, "v1"), truth("ANIMAL", "CAT"), "PENDING", null), source);
	}

	private VisionGroundTruth truth(String category, String tag) {
		return new VisionGroundTruth(List.of(tag), List.of(), List.of(), category, List.of(tag), false);
	}
}
