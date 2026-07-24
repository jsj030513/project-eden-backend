package com.projecteden.dataset;

import java.util.List;
import java.util.Map;

public record UpdateCollectionCaseMetadataCommand(CollectionSourceMetadata source,
		Map<CollectionDimension, String> dimensions, List<String> collectionPlanIds) { }
