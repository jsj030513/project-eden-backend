package com.projecteden.dataset;

import java.util.Map;

/** Offline-only source of benchmark predictions. It never persists a prediction. */
public interface BenchmarkPredictionSource {
	BenchmarkPredictionSourceType type();
	Map<String, VisionGroundTruth> predict(VisionDatasetId datasetId, String revisionId);
}
