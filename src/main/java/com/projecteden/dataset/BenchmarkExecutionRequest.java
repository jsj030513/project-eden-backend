package com.projecteden.dataset;
import java.util.Map;
public record BenchmarkExecutionRequest(VisionDatasetId datasetId, String revisionId, String modelIdentifier, String providerIdentifier, BenchmarkPredictionSourceType predictionSource, Map<String, VisionGroundTruth> suppliedPredictions, String requestedBy, String notes) { }
