package com.projecteden.dataset;

import java.util.List;
import java.util.Map;

public record CollectionCaseMetadata(String schemaVersion, String caseId, CollectionSourceMetadata source,
		Map<CollectionDimension, String> dimensions, List<String> collectionPlanIds,
		boolean eligibleForBenchmark, List<String> validationWarnings) { }
