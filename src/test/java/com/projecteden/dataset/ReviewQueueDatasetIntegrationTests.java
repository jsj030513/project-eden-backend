package com.projecteden.dataset;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.projecteden.memorytaxonomy.evaluation.ImageEvaluationManifestReader;

class ReviewQueueDatasetIntegrationTests {

	@TempDir
	Path root;

	@Test
	void exportsOnlyApprovedOrCorrectedReviewGroundTruthAndExcludesRejected() throws Exception {
		ImageNormalizationService normalization = input -> new NormalizedImage(input.bytes(), "image/jpeg", ImageFormat.JPEG, 2, 2,
				ImageFormat.JPEG, 2, 2, false, false, false, false, false, true,
				Integer.toHexString(java.util.Arrays.hashCode(input.bytes())));
		FilesystemVisionDatasetManager datasets = new FilesystemVisionDatasetManager(normalization, new ObjectMapper(), root);
		FilesystemReviewQueueManager reviews = new FilesystemReviewQueueManager(root);
		VisionDatasetId datasetId = new VisionDatasetId("eden-local");
		datasets.createDataset(new VisionDataset(null, datasetId, "Eden", 1, "ACTIVE", 0));
		importCase(datasets, datasetId, "cat-001");
		importCase(datasets, datasetId, "cat-002");
		importCase(datasets, datasetId, "cat-003");

		VisionGroundTruth prediction = truth("ANIMAL", "CAT");
		ReviewItem approved = reviews.enqueue(datasetId, new VisionDatasetCaseId("cat-001"), prediction, null, null);
		assertThat(read(datasets.exportManifest(datasetId))).isEmpty();
		reviews.approve(datasetId, approved.reviewId(), "reviewer", "approved");
		assertThat(read(datasets.exportManifest(datasetId))).singleElement().extracting(item -> item.caseId()).isEqualTo("cat-001");

		ReviewItem rejected = reviews.enqueue(datasetId, new VisionDatasetCaseId("cat-002"), prediction, null, null);
		reviews.reject(datasetId, rejected.reviewId(), "reviewer", "rejected");
		assertThat(read(datasets.exportManifest(datasetId))).hasSize(1);

		ReviewItem corrected = reviews.enqueue(datasetId, new VisionDatasetCaseId("cat-003"), prediction, null, null);
		reviews.approve(datasetId, corrected.reviewId(), "reviewer", "approved");
		reviews.correct(datasetId, corrected.reviewId(), truth("FOOD", "FOOD"), "reviewer", "corrected");
		assertThat(read(datasets.exportManifest(datasetId)))
				.filteredOn(item -> item.caseId().equals("cat-003"))
				.singleElement()
				.extracting(item -> item.expectedPrimary())
				.isEqualTo("FOOD");
		assertThat(reviews.find(datasetId, corrected.reviewId()).orElseThrow().prediction()).isEqualTo(prediction);
	}

	private List<com.projecteden.memorytaxonomy.evaluation.ImageEvaluationCase> read(Path manifest) {
		return new ImageEvaluationManifestReader(new ObjectMapper()).read(manifest, 10);
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
