package com.projecteden.dataset;

import java.util.List;

public record UpdateCollectionPlanCommand(String name, String description, int targetTotalCases,
		List<CollectionCohort> cohorts, String privacyPolicy) { }
