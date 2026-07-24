package com.projecteden.dataset;

import java.util.Map;

public final class SuppliedBenchmarkPredictionSource implements BenchmarkPredictionSource {
	private final Map<String, VisionGroundTruth> predictions;
	public SuppliedBenchmarkPredictionSource(Map<String, VisionGroundTruth> predictions) { this.predictions = predictions == null ? Map.of() : Map.copyOf(predictions); }
	@Override public BenchmarkPredictionSourceType type() { return BenchmarkPredictionSourceType.SUPPLIED; }
	@Override public Map<String, VisionGroundTruth> predict(VisionDatasetId datasetId, String revisionId) { return predictions; }
}
