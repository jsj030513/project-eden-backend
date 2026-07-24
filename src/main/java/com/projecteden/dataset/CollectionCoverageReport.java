package com.projecteden.dataset;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CollectionCoverageReport(String schemaVersion, String planId, String datasetId, Instant generatedAt,
		int targetTotalCases, int eligibleCases, int ineligibleCases, BigDecimal coveragePercent,
		List<CollectionCohortResult> cohortResults, List<String> missingTargets, List<String> warnings) { }
