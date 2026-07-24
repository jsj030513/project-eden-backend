package com.projecteden.dataset;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "eden.dataset", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "eden.dataset.collection", name = "enabled", havingValue = "true")
public class FilesystemCollectionCaseMetadataManager implements CollectionCaseMetadataManager {
	private final DatasetPathResolver paths;
	@Autowired
	public FilesystemCollectionCaseMetadataManager(@Value("${eden.dataset.root:}") String root) { this(Path.of(root == null || root.isBlank() ? System.getProperty("user.home") + "/.project-eden/datasets" : root)); }
	FilesystemCollectionCaseMetadataManager(Path root) { this.paths = new DatasetPathResolver(root); }
	@Override public CollectionCaseMetadata register(String datasetId, String caseId, RegisterCollectionCaseMetadataCommand command) {
		VisionDatasetId dataset = new VisionDatasetId(datasetId); VisionDatasetCaseId id = new VisionDatasetCaseId(caseId); requireCase(dataset, id);
		Path target = paths.collectionCaseFile(dataset, id); if (Files.exists(target)) throw new IllegalArgumentException("COLLECTION_CASE_METADATA_ALREADY_EXISTS");
		CollectionCaseMetadata metadata = metadata(id, command.source(), command.dimensions(), command.collectionPlanIds()); CollectionFilesystemSupport.atomicWrite(target, metadata); return metadata;
	}
	@Override public CollectionCaseMetadata find(String datasetId, String caseId) { return CollectionFilesystemSupport.read(paths.collectionCaseFile(new VisionDatasetId(datasetId), new VisionDatasetCaseId(caseId)), CollectionCaseMetadata.class, "COLLECTION_CASE_METADATA_NOT_FOUND"); }
	@Override public List<CollectionCaseMetadata> list(String datasetId) {
		VisionDatasetId dataset = new VisionDatasetId(datasetId); Path directory = paths.collectionCaseFile(dataset, new VisionDatasetCaseId("placeholder")).getParent();
		if (!Files.isDirectory(directory) || Files.isSymbolicLink(directory)) return List.of();
		try (var stream = Files.list(directory)) { return stream.filter(path -> path.getFileName().toString().endsWith(".yml")).sorted().map(path -> CollectionFilesystemSupport.read(path, CollectionCaseMetadata.class, "COLLECTION_CASE_METADATA_NOT_FOUND")).sorted(Comparator.comparing(CollectionCaseMetadata::caseId)).toList(); }
		catch (java.io.IOException exception) { throw new IllegalStateException("COLLECTION_METADATA_READ_FAILED", exception); }
	}
	@Override public CollectionCaseMetadata update(String datasetId, String caseId, UpdateCollectionCaseMetadataCommand command) {
		CollectionCaseMetadata current = find(datasetId, caseId); CollectionCaseMetadata updated = metadata(new VisionDatasetCaseId(current.caseId()), command.source(), command.dimensions(), command.collectionPlanIds());
		CollectionFilesystemSupport.atomicWrite(paths.collectionCaseFile(new VisionDatasetId(datasetId), new VisionDatasetCaseId(caseId)), updated); return updated;
	}
	@Override public BenchmarkEligibility evaluateEligibility(String datasetId, String caseId) { CollectionCaseMetadata metadata = find(datasetId, caseId); return eligibility(metadata.source()); }
	private CollectionCaseMetadata metadata(VisionDatasetCaseId id, CollectionSourceMetadata source, java.util.Map<CollectionDimension,String> dimensions, List<String> planIds) {
		validateSource(source); BenchmarkEligibility eligibility = eligibility(source);
		return new CollectionCaseMetadata("eden-collection-case-schema-v1", id.value(), source, CollectionFilesystemSupport.dimensions(dimensions), CollectionFilesystemSupport.planIds(planIds), eligibility.eligible(), eligibility.warnings());
	}
	private void requireCase(VisionDatasetId dataset, VisionDatasetCaseId caseId) { Path file = paths.caseDirectory(dataset, caseId).resolve("case.yml"); if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) throw new IllegalArgumentException("CASE_NOT_FOUND"); }
	private void validateSource(CollectionSourceMetadata source) {
		if (source == null || source.sourceType() == null || source.consentStatus() == null || source.licenseType() == null || source.collectedAt() == null) throw new IllegalArgumentException("INVALID_COLLECTION_SOURCE");
		if (source.collectorId() != null && !source.collectorId().matches("[a-z0-9][a-z0-9-_]{0,63}")) throw new IllegalArgumentException("INVALID_COLLECTOR_ID");
		if (source.originalFilename() != null && (!source.originalFilename().matches("[^/\\\\]{1,255}") || source.originalFilename().equals(".") || source.originalFilename().equals(".."))) throw new IllegalArgumentException("INVALID_ORIGINAL_FILENAME");
	}
	private BenchmarkEligibility eligibility(CollectionSourceMetadata source) {
		List<String> warnings = new ArrayList<>(); boolean eligible = switch (source.sourceType()) {
			case SYNTHETIC, DEVELOPER_CAPTURED -> source.consentStatus() == CollectionConsentStatus.NOT_REQUIRED;
			case CONSENTED_PARTICIPANT -> source.consentStatus() == CollectionConsentStatus.EXPLICITLY_GRANTED;
			case PUBLIC_LICENSED -> source.consentStatus() == CollectionConsentStatus.NOT_REQUIRED && (source.licenseType() == CollectionLicenseType.CC0 || source.licenseType() == CollectionLicenseType.CC_BY || source.licenseType() == CollectionLicenseType.OTHER_APPROVED);
			case UNKNOWN -> false;
		};
		if (!eligible) warnings.add("BENCHMARK_INELIGIBLE_SOURCE_OR_CONSENT"); return new BenchmarkEligibility(eligible, List.copyOf(warnings));
	}
}
