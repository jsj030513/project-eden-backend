package com.projecteden.dataset;

import java.math.BigDecimal;

public record CollectionCohortResult(String cohortId, int targetCases, int matchedCases, int remainingCases,
		BigDecimal coveragePercent, CollectionCohortStatus status) { }
