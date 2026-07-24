package com.projecteden.dataset;

import java.math.BigDecimal;
import java.util.List;

public record BenchmarkMetrics(
		BigDecimal categoryAccuracy,
		BigDecimal tagPrecision,
		BigDecimal tagRecall,
		BigDecimal tagF1,
		BigDecimal objectPrecision,
		BigDecimal objectRecall,
		BigDecimal activityPrecision,
		BigDecimal activityRecall,
		BigDecimal relationshipPrecision,
		BigDecimal relationshipRecall,
		BigDecimal fallbackRate,
		BigDecimal unknownRate,
		int caseCount) {

	public BenchmarkMetrics {
		if (caseCount < 0) throw new IllegalArgumentException("INVALID_BENCHMARK_CASE_COUNT");
		for (BigDecimal value : List.of(categoryAccuracy, tagPrecision, tagRecall, tagF1, objectPrecision, objectRecall,
				activityPrecision, activityRecall, relationshipPrecision, relationshipRecall, fallbackRate, unknownRate)) {
			if (value == null || value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
				throw new IllegalArgumentException("INVALID_BENCHMARK_METRIC");
			}
		}
	}
}
