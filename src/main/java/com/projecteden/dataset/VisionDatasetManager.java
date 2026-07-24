package com.projecteden.dataset;

/** Filesystem-only boundary; no JPA entity, database write, controller, or public API. */
public interface VisionDatasetManager {
	VisionDataset createDataset(VisionDataset dataset);
	java.util.Optional<VisionDataset> findDataset(VisionDatasetId id);
	java.util.List<VisionDataset> listDatasets();
	VisionDatasetCase importCase(VisionDatasetId datasetId, VisionDatasetCase input, java.nio.file.Path sourceImage);
	java.util.Optional<VisionDatasetCase> findCase(VisionDatasetId datasetId, VisionDatasetCaseId caseId);
	java.util.List<VisionDatasetCase> listCases(VisionDatasetId datasetId);
	VisionDatasetCase archiveCase(VisionDatasetId datasetId, VisionDatasetCaseId caseId);
	java.nio.file.Path exportManifest(VisionDatasetId datasetId);
}
