package com.projecteden.dataset;

import java.util.List;

public interface CollectionCaseMetadataManager {
	CollectionCaseMetadata register(String datasetId, String caseId, RegisterCollectionCaseMetadataCommand command);
	CollectionCaseMetadata find(String datasetId, String caseId);
	List<CollectionCaseMetadata> list(String datasetId);
	CollectionCaseMetadata update(String datasetId, String caseId, UpdateCollectionCaseMetadataCommand command);
	BenchmarkEligibility evaluateEligibility(String datasetId, String caseId);
}
