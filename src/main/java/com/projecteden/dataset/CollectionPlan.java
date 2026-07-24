package com.projecteden.dataset;

import java.time.Instant;
import java.util.List;

public record CollectionPlan(String schemaVersion, String planId, String name, String description,
		CollectionPlanStatus status, Instant createdAt, Instant updatedAt, int targetTotalCases,
		List<CollectionCohort> cohorts, String privacyPolicy) { }
