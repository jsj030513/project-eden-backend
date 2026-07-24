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

class FilesystemDatasetCollectionPlanManagerTests {
	@TempDir Path root;

	@Test void createsListsUpdatesAndTransitionsPlansWithoutMutatingCreatedAt() throws Exception {
		fixtureDataset(); FilesystemDatasetCollectionPlanManager manager = new FilesystemDatasetCollectionPlanManager(root);
		CollectionPlan draft = manager.createPlan(command("cat-plan", 20, List.of(cohort("bright", 10, Map.of(CollectionDimension.LIGHTING, "BRIGHT")))));
		assertThat(draft.status()).isEqualTo(CollectionPlanStatus.DRAFT); assertThat(manager.findPlan("eden-local", "cat-plan")).isEqualTo(draft); assertThat(manager.listPlans("eden-local")).containsExactly(draft);
		CollectionPlan updated = manager.updatePlan("eden-local", "cat-plan", new UpdateCollectionPlanCommand("Updated", "description", 20, List.of(cohort("bright", 10, Map.of(CollectionDimension.LIGHTING, "BRIGHT"))), "local-only"));
		assertThat(updated.createdAt()).isEqualTo(draft.createdAt()); assertThat(updated.name()).isEqualTo("Updated"); assertThat(manager.activatePlan("eden-local", "cat-plan").status()).isEqualTo(CollectionPlanStatus.ACTIVE);
		assertThat(manager.completePlan("eden-local", "cat-plan").status()).isEqualTo(CollectionPlanStatus.COMPLETED); assertThat(manager.archivePlan("eden-local", "cat-plan").status()).isEqualTo(CollectionPlanStatus.ARCHIVED);
		assertThatThrownBy(() -> manager.activatePlan("eden-local", "cat-plan")).hasMessageContaining("INVALID_COLLECTION_PLAN_TRANSITION");
		assertThat(Files.list(root.resolve("datasets/eden-local/collection/plans")).map(path -> path.getFileName().toString()).toList()).containsExactly("cat-plan.yml");
	}

	@Test void rejectsInvalidPlanInputAndUnsafePaths() throws Exception {
		fixtureDataset(); FilesystemDatasetCollectionPlanManager manager = new FilesystemDatasetCollectionPlanManager(root);
		assertThatThrownBy(() -> manager.createPlan(command("../escape", 1, List.of()))).hasMessageContaining("INVALID_COLLECTION_PLAN_ID");
		assertThatThrownBy(() -> manager.createPlan(command("valid", 0, List.of()))).hasMessageContaining("INVALID_COLLECTION_PLAN");
		assertThatThrownBy(() -> manager.createPlan(command("valid", 1, List.of(cohort("one", 2, Map.of()))))).hasMessageContaining("EXCEEDS");
		assertThatThrownBy(() -> manager.createPlan(command("valid", 2, List.of(cohort("same", 1, Map.of()), cohort("same", 1, Map.of()))))).hasMessageContaining("DUPLICATE");
		assertThatThrownBy(() -> manager.createPlan(command("bad-value", 1, List.of(cohort("one", 1, Map.of(CollectionDimension.LIGHTING, "bright")))))).hasMessageContaining("INVALID_COLLECTION_DIMENSION");
		Path collection = root.resolve("datasets/eden-local/collection"); Path outside = Files.createTempDirectory("collection-outside"); Files.createDirectories(collection); Files.createSymbolicLink(collection.resolve("plans"), outside);
		assertThatThrownBy(() -> manager.createPlan(command("symlink", 1, List.of()))).hasMessageContaining("Symlink");
	}

	private CreateCollectionPlanCommand command(String id, int target, List<CollectionCohort> cohorts) { return new CreateCollectionPlanCommand("eden-local", id, "Plan " + id, "local plan", target, cohorts, "no-personal-data"); }
	private CollectionCohort cohort(String id, int target, Map<CollectionDimension,String> dimensions) { return new CollectionCohort(id, id, null, target, dimensions, List.of(), List.of()); }
	private void fixtureDataset() throws Exception { Path dataset = root.resolve("datasets/eden-local"); Files.createDirectories(dataset); CollectionFilesystemSupport.atomicWrite(dataset.resolve("dataset.yml"), new VisionDataset("v1", new VisionDatasetId("eden-local"), "Dataset", 1, "ACTIVE", 0)); }
}
