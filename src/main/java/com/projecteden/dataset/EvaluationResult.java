package com.projecteden.dataset;

import java.util.List;

public record EvaluationResult(BenchmarkMetrics metrics, EvaluationProgress progress, List<EvaluationCase> cases) {
	public EvaluationResult {
		cases = cases == null ? List.of() : List.copyOf(cases);
	}
}
