package com.projecteden.dataset;

import java.util.List;
import java.util.Optional;

/** Filesystem-only benchmark metadata boundary; metrics are supplied by a future evaluator. */
public interface BenchmarkManager {
	BenchmarkRun createRun(VisionDatasetId datasetId, String revisionId, String model, String provider);
	BenchmarkRun finishRun(VisionDatasetId datasetId, String runId, BenchmarkMetrics metrics);
	BenchmarkRun markRunning(VisionDatasetId datasetId, String runId);
	BenchmarkRun failRun(VisionDatasetId datasetId, String runId, BenchmarkFailure failure);
	Optional<BenchmarkRun> findRun(VisionDatasetId datasetId, String runId);
	List<BenchmarkRun> listRuns(VisionDatasetId datasetId);
	Optional<BenchmarkRun> latestRun(VisionDatasetId datasetId);
}
