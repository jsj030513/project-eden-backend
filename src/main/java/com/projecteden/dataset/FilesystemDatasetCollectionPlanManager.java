package com.projecteden.dataset;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "eden.dataset", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "eden.dataset.collection", name = "enabled", havingValue = "true")
public class FilesystemDatasetCollectionPlanManager implements DatasetCollectionPlanManager {
	private final DatasetPathResolver paths;
	private final FilesystemCollectionCaseMetadataManager cases;
	@Autowired
	public FilesystemDatasetCollectionPlanManager(@Value("${eden.dataset.root:}") String root) { this(Path.of(root == null || root.isBlank() ? System.getProperty("user.home") + "/.project-eden/datasets" : root)); }
	FilesystemDatasetCollectionPlanManager(Path root) { this.paths = new DatasetPathResolver(root); this.cases = new FilesystemCollectionCaseMetadataManager(root); }
	@Override public CollectionPlan createPlan(CreateCollectionPlanCommand command) {
		VisionDatasetId dataset = new VisionDatasetId(command.datasetId()); requireDataset(dataset); CollectionFilesystemSupport.requireSafeId(command.planId(), "INVALID_COLLECTION_PLAN_ID");
		Path target = paths.collectionPlanFile(dataset, command.planId()); if (Files.exists(target)) throw new IllegalArgumentException("COLLECTION_PLAN_ALREADY_EXISTS");
		Instant now = CollectionFilesystemSupport.now(); CollectionPlan plan = plan(command.planId(), command.name(), command.description(), CollectionPlanStatus.DRAFT, now, now, command.targetTotalCases(), command.cohorts(), command.privacyPolicy());
		CollectionFilesystemSupport.atomicWrite(target, plan); return plan;
	}
	@Override public CollectionPlan findPlan(String datasetId, String planId) { VisionDatasetId dataset = new VisionDatasetId(datasetId); CollectionFilesystemSupport.requireSafeId(planId, "INVALID_COLLECTION_PLAN_ID"); return CollectionFilesystemSupport.read(paths.collectionPlanFile(dataset, planId), CollectionPlan.class, "COLLECTION_PLAN_NOT_FOUND"); }
	@Override public List<CollectionPlan> listPlans(String datasetId) {
		VisionDatasetId dataset = new VisionDatasetId(datasetId); Path directory = paths.collectionPlanFile(dataset, "placeholder").getParent();
		if (!Files.isDirectory(directory) || Files.isSymbolicLink(directory)) return List.of();
		try (var stream = Files.list(directory)) { return stream.filter(path -> path.getFileName().toString().endsWith(".yml")).sorted().map(path -> CollectionFilesystemSupport.read(path, CollectionPlan.class, "COLLECTION_PLAN_NOT_FOUND")).sorted(Comparator.comparing(CollectionPlan::planId)).toList(); }
		catch (java.io.IOException exception) { throw new IllegalStateException("COLLECTION_PLAN_READ_FAILED", exception); }
	}
	@Override public CollectionPlan updatePlan(String datasetId, String planId, UpdateCollectionPlanCommand command) {
		CollectionPlan current = findPlan(datasetId, planId); if (current.status() == CollectionPlanStatus.COMPLETED || current.status() == CollectionPlanStatus.ARCHIVED) throw new IllegalArgumentException("COLLECTION_PLAN_IMMUTABLE");
		CollectionPlan updated = plan(current.planId(), command.name(), command.description(), current.status(), current.createdAt(), CollectionFilesystemSupport.now(), command.targetTotalCases(), command.cohorts(), command.privacyPolicy());
		CollectionFilesystemSupport.atomicWrite(paths.collectionPlanFile(new VisionDatasetId(datasetId), planId), updated); return updated;
	}
	@Override public CollectionPlan activatePlan(String datasetId, String planId) { return transition(datasetId, planId, CollectionPlanStatus.DRAFT, CollectionPlanStatus.ACTIVE); }
	@Override public CollectionPlan completePlan(String datasetId, String planId) { return transition(datasetId, planId, CollectionPlanStatus.ACTIVE, CollectionPlanStatus.COMPLETED); }
	@Override public CollectionPlan archivePlan(String datasetId, String planId) {
		CollectionPlan current = findPlan(datasetId, planId); if (current.status() == CollectionPlanStatus.ARCHIVED) throw new IllegalArgumentException("INVALID_COLLECTION_PLAN_TRANSITION");
		CollectionPlan archived = new CollectionPlan(current.schemaVersion(), current.planId(), current.name(), current.description(), CollectionPlanStatus.ARCHIVED, current.createdAt(), CollectionFilesystemSupport.now(), current.targetTotalCases(), current.cohorts(), current.privacyPolicy());
		CollectionFilesystemSupport.atomicWrite(paths.collectionPlanFile(new VisionDatasetId(datasetId), planId), archived); return archived;
	}
	@Override public CollectionCoverageReport generateCoverage(String datasetId, String planId) {
		VisionDatasetId dataset = new VisionDatasetId(datasetId); CollectionPlan plan = findPlan(datasetId, planId); List<CollectionCaseMetadata> assigned = cases.list(datasetId).stream().filter(metadata -> metadata.collectionPlanIds().contains(planId)).toList();
		int eligible = (int) assigned.stream().filter(CollectionCaseMetadata::eligibleForBenchmark).count(); int ineligible = assigned.size() - eligible;
		List<CollectionCohortResult> results = new ArrayList<>(); List<String> missing = new ArrayList<>();
		for (CollectionCohort cohort : plan.cohorts()) {
			int matched = (int) assigned.stream().filter(CollectionCaseMetadata::eligibleForBenchmark).filter(metadata -> matches(dataset, metadata, cohort)).count(); int remaining = Math.max(0, cohort.targetCases() - matched);
			CollectionCohortStatus status = matched == 0 ? CollectionCohortStatus.NOT_STARTED : matched < cohort.targetCases() ? CollectionCohortStatus.INSUFFICIENT : matched == cohort.targetCases() ? CollectionCohortStatus.TARGET_MET : CollectionCohortStatus.EXCEEDED;
			if (remaining > 0) missing.add(cohort.cohortId()); results.add(new CollectionCohortResult(cohort.cohortId(), cohort.targetCases(), matched, remaining, CollectionFilesystemSupport.percent(matched, cohort.targetCases()), status));
		}
		int allocated = plan.cohorts().stream().mapToInt(CollectionCohort::targetCases).sum(); List<String> warnings = allocated < plan.targetTotalCases() ? List.of("UNALLOCATED_TARGET_CASES") : List.of();
		CollectionCoverageReport report = new CollectionCoverageReport("eden-collection-coverage-schema-v1", planId, datasetId, CollectionFilesystemSupport.now(), plan.targetTotalCases(), eligible, ineligible, CollectionFilesystemSupport.percent(eligible, plan.targetTotalCases()), List.copyOf(results), List.copyOf(missing), warnings);
		CollectionFilesystemSupport.atomicWrite(paths.collectionCoverageFile(dataset, planId), report); return report;
	}
	private CollectionPlan transition(String datasetId, String planId, CollectionPlanStatus from, CollectionPlanStatus to) {
		CollectionPlan current = findPlan(datasetId, planId); if (current.status() != from) throw new IllegalArgumentException("INVALID_COLLECTION_PLAN_TRANSITION");
		CollectionPlan updated = new CollectionPlan(current.schemaVersion(), current.planId(), current.name(), current.description(), to, current.createdAt(), CollectionFilesystemSupport.now(), current.targetTotalCases(), current.cohorts(), current.privacyPolicy());
		CollectionFilesystemSupport.atomicWrite(paths.collectionPlanFile(new VisionDatasetId(datasetId), planId), updated); return updated;
	}
	private CollectionPlan plan(String planId, String name, String description, CollectionPlanStatus status, Instant created, Instant updated, int target, List<CollectionCohort> cohorts, String privacyPolicy) {
		if (name == null || name.isBlank() || target <= 0) throw new IllegalArgumentException("INVALID_COLLECTION_PLAN"); List<CollectionCohort> normalized = normalizeCohorts(cohorts);
		int cohortTargets = normalized.stream().mapToInt(CollectionCohort::targetCases).sum(); if (cohortTargets > target) throw new IllegalArgumentException("COLLECTION_COHORT_TARGET_EXCEEDS_PLAN_TARGET");
		return new CollectionPlan("eden-collection-plan-schema-v1", planId, name.trim(), description, status, created, updated, target, normalized, privacyPolicy);
	}
	private List<CollectionCohort> normalizeCohorts(List<CollectionCohort> cohorts) {
		if (cohorts == null) return List.of(); Set<String> ids = new HashSet<>(); List<CollectionCohort> result = new ArrayList<>();
		for (CollectionCohort cohort : cohorts) { if (cohort == null || cohort.targetCases() <= 0 || cohort.name() == null || cohort.name().isBlank()) throw new IllegalArgumentException("INVALID_COLLECTION_COHORT"); CollectionFilesystemSupport.requireSafeId(cohort.cohortId(), "INVALID_COLLECTION_COHORT_ID"); if (!ids.add(cohort.cohortId())) throw new IllegalArgumentException("DUPLICATE_COLLECTION_COHORT_ID");
			List<String> required = CollectionFilesystemSupport.codes(cohort.requiredTags(), "INVALID_COLLECTION_TAG"); List<String> excluded = CollectionFilesystemSupport.codes(cohort.excludedTags(), "INVALID_COLLECTION_TAG"); if (required.stream().anyMatch(excluded::contains)) throw new IllegalArgumentException("CONFLICTING_COLLECTION_TAG");
			result.add(new CollectionCohort(cohort.cohortId(), cohort.name().trim(), cohort.description(), cohort.targetCases(), CollectionFilesystemSupport.dimensions(cohort.dimensions()), required, excluded)); }
		return result.stream().sorted(Comparator.comparing(CollectionCohort::cohortId)).toList();
	}
	private boolean matches(VisionDatasetId dataset, CollectionCaseMetadata metadata, CollectionCohort cohort) {
		if (!metadata.dimensions().entrySet().containsAll(cohort.dimensions().entrySet())) return false;
		VisionDatasetCase stored = CollectionFilesystemSupport.read(paths.caseDirectory(dataset, new VisionDatasetCaseId(metadata.caseId())).resolve("case.yml"), VisionDatasetCase.class, "CASE_NOT_FOUND");
		Set<String> tags = Set.copyOf(stored.groundTruth() == null || stored.groundTruth().tags() == null ? List.of() : stored.groundTruth().tags());
		return tags.containsAll(cohort.requiredTags()) && cohort.excludedTags().stream().noneMatch(tags::contains);
	}
	private void requireDataset(VisionDatasetId dataset) { Path file = paths.dataset(dataset).resolve("dataset.yml"); if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) throw new IllegalArgumentException("DATASET_NOT_FOUND"); }
}
