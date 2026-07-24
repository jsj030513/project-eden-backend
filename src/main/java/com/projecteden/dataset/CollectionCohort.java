package com.projecteden.dataset;

import java.util.List;
import java.util.Map;

public record CollectionCohort(String cohortId, String name, String description, int targetCases,
		Map<CollectionDimension, String> dimensions, List<String> requiredTags, List<String> excludedTags) { }
