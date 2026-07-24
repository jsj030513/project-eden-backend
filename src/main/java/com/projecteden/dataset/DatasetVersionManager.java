package com.projecteden.dataset;

import java.util.List;
import java.util.Optional;

/** Filesystem-only immutable snapshot boundary; no entity, database write, controller, or public API. */
public interface DatasetVersionManager {
	DatasetRevision createRevision(VisionDatasetId datasetId, RevisionMetadata metadata);
	Optional<DatasetRevision> find(VisionDatasetId datasetId, String revisionId);
	List<DatasetRevision> list(VisionDatasetId datasetId);
}
