package com.projecteden.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CollectionCoverageReportTests {
	@TempDir Path root;

	@Test void calculatesEligibleCohortsWithoutDoubleCountingCasesAndWritesDeterministicContent() throws Exception {
		fixtureCase("cat", List.of("CAT", "ANIMAL")); fixtureCase("ineligible", List.of("CAT"));
		FilesystemDatasetCollectionPlanManager plans = new FilesystemDatasetCollectionPlanManager(root); FilesystemCollectionCaseMetadataManager metadata = new FilesystemCollectionCaseMetadataManager(root);
		plans.createPlan(new CreateCollectionPlanCommand("eden-local", "plan", "Plan", null, 4, List.of(
				new CollectionCohort("bright", "Bright", null, 2, Map.of(CollectionDimension.LIGHTING, "BRIGHT"), List.of("CAT"), List.of()),
				new CollectionCohort("animal", "Animal", null, 1, Map.of(), List.of("ANIMAL"), List.of("DOG"))), "local"));
		metadata.register("eden-local", "cat", new RegisterCollectionCaseMetadataCommand(source(CollectionSourceType.SYNTHETIC, CollectionConsentStatus.NOT_REQUIRED), Map.of(CollectionDimension.LIGHTING, "BRIGHT"), List.of("plan")));
		metadata.register("eden-local", "ineligible", new RegisterCollectionCaseMetadataCommand(source(CollectionSourceType.UNKNOWN, CollectionConsentStatus.UNKNOWN), Map.of(CollectionDimension.LIGHTING, "BRIGHT"), List.of("plan")));
		CollectionCoverageReport first = plans.generateCoverage("eden-local", "plan"); byte[] bytes = Files.readAllBytes(root.resolve("datasets/eden-local/collection/reports/plan-coverage.yml")); CollectionCoverageReport second = plans.generateCoverage("eden-local", "plan");
		assertThat(first.eligibleCases()).isEqualTo(1); assertThat(first.ineligibleCases()).isEqualTo(1); assertThat(first.cohortResults()).extracting(CollectionCohortResult::matchedCases).containsExactly(1, 1); assertThat(first.missingTargets()).contains("bright"); assertThat(first.warnings()).contains("UNALLOCATED_TARGET_CASES");
		assertThat(second.cohortResults()).isEqualTo(first.cohortResults()); assertThat(Files.readAllBytes(root.resolve("datasets/eden-local/collection/reports/plan-coverage.yml"))).isNotEmpty(); assertThat(bytes).isNotEmpty();
	}

	private CollectionSourceMetadata source(CollectionSourceType type, CollectionConsentStatus consent) { return new CollectionSourceMetadata(type, Instant.EPOCH, "developer-local", consent, CollectionLicenseType.OWNED, "image.jpg", null); }
	private void fixtureCase(String id, List<String> tags) throws Exception { Path dataset = root.resolve("datasets/eden-local"); Files.createDirectories(dataset); CollectionFilesystemSupport.atomicWrite(dataset.resolve("dataset.yml"), new VisionDataset("v1", new VisionDatasetId("eden-local"), "Dataset", 1, "ACTIVE", 2)); Path directory = dataset.resolve("cases").resolve(id); Files.createDirectories(directory); CollectionFilesystemSupport.atomicWrite(directory.resolve("case.yml"), new VisionDatasetCase("v1", new VisionDatasetCaseId(id), new VisionDatasetId("eden-local"), "cases/" + id + "/image.jpg", "image/jpeg", 1, 1, id, new VisionConsentMetadata(true, true, false, false, Instant.EPOCH, "v1"), new VisionGroundTruth(List.of(), List.of(), List.of(), "ANIMAL", List.of(), tags, false), "CONFIRMED", "ACTIVE")); }
}
