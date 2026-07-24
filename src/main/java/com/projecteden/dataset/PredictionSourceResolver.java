package com.projecteden.dataset;

import java.util.Map;
import java.util.function.Function;

public final class PredictionSourceResolver {
	private final Map<BenchmarkPredictionSourceType, Function<BenchmarkExecutionRequest, BenchmarkPredictionSource>> factories;
	public PredictionSourceResolver(Map<BenchmarkPredictionSourceType, Function<BenchmarkExecutionRequest, BenchmarkPredictionSource>> factories) { this.factories = Map.copyOf(factories); }
	public BenchmarkPredictionSource resolve(BenchmarkExecutionRequest request) {
		Function<BenchmarkExecutionRequest, BenchmarkPredictionSource> factory = factories.get(request.predictionSource());
		if (factory == null) throw new IllegalArgumentException(request.predictionSource() == BenchmarkPredictionSourceType.LOCAL ? "LOCAL_PROVIDER_DISABLED" : "PREDICTION_SOURCE_UNSUPPORTED");
		return factory.apply(request);
	}
}
