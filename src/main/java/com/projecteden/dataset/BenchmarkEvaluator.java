package com.projecteden.dataset;

import java.util.Map;

/** Pure revision-manifest comparison boundary; it never invokes a model or provider. */
public interface BenchmarkEvaluator {
	EvaluationResult evaluate(VisionDatasetId datasetId, String revisionId, Map<String, VisionGroundTruth> predictions);
	EvaluationResult evaluateRevision(VisionDatasetId datasetId, String revisionId, Map<String, VisionGroundTruth> predictions);
	EvaluationCase evaluateCase(String caseId, VisionGroundTruth prediction, VisionGroundTruth groundTruth);
	BenchmarkRun evaluateAndFinish(VisionDatasetId datasetId, String revisionId, String runId, Map<String, VisionGroundTruth> predictions);
}
