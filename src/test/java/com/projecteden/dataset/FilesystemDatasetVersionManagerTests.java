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
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.projecteden.imagenormalization.ImageFormat;
import com.projecteden.imagenormalization.ImageNormalizationService;
import com.projecteden.imagenormalization.NormalizedImage;

class FilesystemDatasetVersionManagerTests {

	@TempDir
	Path root;

	@Test
	void createsImmutableSnapshotWithChecksumsAndRejectsDuplicateState() throws Exception {
		FilesystemVisionDatasetManager datasets = datasetManager();
		FilesystemReviewQueueManager reviews = new FilesystemReviewQueueManager(root);
		FilesystemDatasetVersionManager versions = new FilesystemDatasetVersionManager(root);
		VisionDatasetId datasetId = new VisionDatasetId("eden-local");
		datasets.createDataset(new VisionDataset(null, datasetId, "Eden", 1, "ACTIVE", 0));
		importCase(datasets, datasetId, "cat-001");
		ReviewItem approved = reviews.enqueue(datasetId, new VisionDatasetCaseId("cat-001"), truth("ANIMAL", "CAT"), "reviewer", null);
		reviews.approve(datasetId, approved.reviewId(), "reviewer", "approved");
		datasets.exportManifest(datasetId);

		DatasetRevision first = versions.createRevision(datasetId, new RevisionMetadata("reviewer", "approved cat"));
		Path firstDirectory = root.resolve("datasets/eden-local/revisions/" + first.revisionId());
		assertThat(first.revisionId()).isEqualTo("rev-000001");
		assertThat(first.status()).isEqualTo(RevisionStatus.ACTIVE);
		assertThat(first.manifestChecksum()).hasSize(64);
		assertThat(Files.readAllBytes(firstDirectory.resolve("dataset.yml"))).isEqualTo(Files.readAllBytes(root.resolve("datasets/eden-local/dataset.yml")));
		assertThat(Files.exists(firstDirectory.resolve("manifest.yml"))).isTrue();
		assertThat(Files.exists(firstDirectory.resolve("summary.yml"))).isTrue();
		DatasetSnapshot snapshot = yaml().readValue(firstDirectory.resolve("summary.yml").toFile(), DatasetSnapshot.class);
		assertThat(snapshot.caseCount()).isEqualTo(1);
		assertThat(snapshot.approvedCount()).isEqualTo(1);
		assertThat(snapshot.categoryCount()).containsEntry("ANIMAL", 1);
		assertThat(snapshot.tagCount()).containsEntry("CAT", 1);
		assertThatThrownBy(() -> versions.createRevision(datasetId, new RevisionMetadata("reviewer", "duplicate")))
				.hasMessageContaining("DUPLICATE_REVISION_SNAPSHOT");

		byte[] firstManifest = Files.readAllBytes(firstDirectory.resolve("manifest.yml"));
		datasets.archiveCase(datasetId, new VisionDatasetCaseId("cat-001"));
		datasets.exportManifest(datasetId);
		DatasetRevision second = versions.createRevision(datasetId, new RevisionMetadata("reviewer", "archive"));
		assertThat(second.revisionId()).isEqualTo("rev-000002");
		assertThat(versions.list(datasetId)).extracting(DatasetRevision::revisionId).containsExactly("rev-000001", "rev-000002");
		assertThat(Files.readAllBytes(firstDirectory.resolve("manifest.yml"))).isEqualTo(firstManifest);
	}

	@Test
	void keepsByteForByteRevisionImageAfterLiveImageOverwriteAndDeletion() throws Exception {
		FilesystemVisionDatasetManager datasets = datasetManager();
		FilesystemReviewQueueManager reviews = new FilesystemReviewQueueManager(root);
		FilesystemDatasetVersionManager versions = new FilesystemDatasetVersionManager(root);
		VisionDatasetId datasetId = new VisionDatasetId("eden-local");
		datasets.createDataset(new VisionDataset(null, datasetId, "Eden", 1, "ACTIVE", 0));
		importCase(datasets, datasetId, "cat-001");
		ReviewItem approved = reviews.enqueue(datasetId, new VisionDatasetCaseId("cat-001"), truth("ANIMAL", "CAT"), "reviewer", null);
		reviews.approve(datasetId, approved.reviewId(), "reviewer", "approved");
		datasets.exportManifest(datasetId);
		DatasetRevision revision = versions.createRevision(datasetId, new RevisionMetadata("reviewer", "snapshot"));
		Path revisionDirectory = root.resolve("datasets/eden-local/revisions/").resolve(revision.revisionId());
		Path revisionImage = revisionDirectory.resolve("images/cat-001.jpg");
		byte[] snapshotBytes = Files.readAllBytes(revisionImage);
		byte[] manifestBytes = Files.readAllBytes(revisionDirectory.resolve("manifest.yml"));
		byte[] revisionBytes = Files.readAllBytes(revisionDirectory.resolve("revision.yml"));
		Path liveImage = root.resolve("datasets/eden-local/cases/cat-001/image.jpg");
		Files.write(liveImage, "changed-live-image".getBytes());
		Files.delete(liveImage);
		assertThat(Files.readAllBytes(revisionImage)).isEqualTo(snapshotBytes);
		assertThat(Files.readAllBytes(revisionDirectory.resolve("manifest.yml"))).isEqualTo(manifestBytes);
		assertThat(Files.readAllBytes(revisionDirectory.resolve("revision.yml"))).isEqualTo(revisionBytes);
		assertThat(new RevisionImageResolver(root).resolve(datasetId, revision.revisionId(), "images/cat-001.jpg")).isEqualTo(revisionImage);
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

	private ObjectMapper yaml() {
		return new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());
	}
}
