package com.projecteden.memorytaxonomy.evaluation;

import java.util.Map;

public record ImageEvaluationSummary(
		int totalCases,
		int scoredPrimaryCases,
		int providerSuccessCount,
		int providerFailureCount,
		int mockFallbackCount,
		int unknownCount,
		double primaryAccuracy,
		double secondaryPrecision,
		double secondaryRecall,
		double tagPrecision,
		double tagRecall,
		double averageLatencyMs,
		long p50LatencyMs,
		long p95LatencyMs,
		Map<String, Long> mimeBreakdown,
		Map<String, Long> failureBreakdown) {
}
