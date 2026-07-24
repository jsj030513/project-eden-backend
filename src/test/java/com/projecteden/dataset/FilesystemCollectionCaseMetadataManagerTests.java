package com.projecteden.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemCollectionCaseMetadataManagerTests {
	@TempDir Path root;

	@Test void registersEligibleAndIneligibleSourceMetadata() throws Exception {
		caseFixture("synthetic"); caseFixture("pending"); FilesystemCollectionCaseMetadataManager manager = new FilesystemCollectionCaseMetadataManager(root);
		CollectionCaseMetadata synthetic = manager.register("eden-local", "synthetic", command(CollectionSourceType.SYNTHETIC, CollectionConsentStatus.NOT_REQUIRED, CollectionLicenseType.OWNED));
		CollectionCaseMetadata pending = manager.register("eden-local", "pending", command(CollectionSourceType.CONSENTED_PARTICIPANT, CollectionConsentStatus.PENDING, CollectionLicenseType.OWNED));
		assertThat(synthetic.eligibleForBenchmark()).isTrue(); assertThat(manager.evaluateEligibility("eden-local", "synthetic").eligible()).isTrue(); assertThat(pending.eligibleForBenchmark()).isFalse(); assertThat(pending.validationWarnings()).contains("BENCHMARK_INELIGIBLE_SOURCE_OR_CONSENT");
		assertThat(manager.list("eden-local")).extracting(CollectionCaseMetadata::caseId).containsExactly("pending", "synthetic");
	}

	@Test void validatesCollectorDimensionsAndExistingCase() throws Exception {
		caseFixture("cat"); FilesystemCollectionCaseMetadataManager manager = new FilesystemCollectionCaseMetadataManager(root);
		assertThatThrownBy(() -> manager.register("eden-local", "missing", command(CollectionSourceType.SYNTHETIC, CollectionConsentStatus.NOT_REQUIRED, CollectionLicenseType.OWNED))).hasMessageContaining("CASE_NOT_FOUND");
		CollectionSourceMetadata unsafe = new CollectionSourceMetadata(CollectionSourceType.SYNTHETIC, Instant.EPOCH, "Developer Name", CollectionConsentStatus.NOT_REQUIRED, CollectionLicenseType.OWNED, "cat.jpg", null);
		assertThatThrownBy(() -> manager.register("eden-local", "cat", new RegisterCollectionCaseMetadataCommand(unsafe, Map.of(), List.of("plan")))).hasMessageContaining("INVALID_COLLECTOR_ID");
		CollectionSourceMetadata pathFilename = new CollectionSourceMetadata(CollectionSourceType.SYNTHETIC, Instant.EPOCH, "developer-local", CollectionConsentStatus.NOT_REQUIRED, CollectionLicenseType.OWNED, "/private/cat.jpg", null);
		assertThatThrownBy(() -> manager.register("eden-local", "cat", new RegisterCollectionCaseMetadataCommand(pathFilename, Map.of(), List.of("plan")))).hasMessageContaining("INVALID_ORIGINAL_FILENAME");
		assertThatThrownBy(() -> manager.register("eden-local", "cat", new RegisterCollectionCaseMetadataCommand(source(CollectionSourceType.SYNTHETIC, CollectionConsentStatus.NOT_REQUIRED, CollectionLicenseType.OWNED), Map.of(CollectionDimension.DISTANCE, "near"), List.of("plan")))).hasMessageContaining("INVALID_COLLECTION_DIMENSION");
	}

	private RegisterCollectionCaseMetadataCommand command(CollectionSourceType type, CollectionConsentStatus consent, CollectionLicenseType license) { return new RegisterCollectionCaseMetadataCommand(source(type, consent, license), Map.of(CollectionDimension.LIGHTING, "NORMAL"), List.of("plan")); }
	private CollectionSourceMetadata source(CollectionSourceType type, CollectionConsentStatus consent, CollectionLicenseType license) { return new CollectionSourceMetadata(type, Instant.EPOCH, "developer-local", consent, license, "image.jpg", null); }
	private void caseFixture(String id) throws Exception { Path directory = root.resolve("datasets/eden-local/cases").resolve(id); Files.createDirectories(directory); VisionDatasetCase value = new VisionDatasetCase("v1", new VisionDatasetCaseId(id), new VisionDatasetId("eden-local"), "cases/" + id + "/image.jpg", "image/jpeg", 1, 1, id, new VisionConsentMetadata(true, true, false, false, Instant.EPOCH, "v1"), new VisionGroundTruth(List.of(), List.of(), List.of(), "ANIMAL", List.of(), List.of("CAT"), false), "CONFIRMED", "ACTIVE"); CollectionFilesystemSupport.atomicWrite(directory.resolve("case.yml"), value); }
}
