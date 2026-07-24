package com.projecteden.dataset;

import java.util.List;

public record CreateCollectionPlanCommand(String datasetId, String planId, String name, String description,
		int targetTotalCases, List<CollectionCohort> cohorts, String privacyPolicy) { }
