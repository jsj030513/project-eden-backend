package com.projecteden.dataset;

/** Resolves an offline prediction source for a single benchmark execution. */
public interface BenchmarkPredictionSourceResolver {
	BenchmarkPredictionSource resolve(BenchmarkExecutionRequest request);
}
