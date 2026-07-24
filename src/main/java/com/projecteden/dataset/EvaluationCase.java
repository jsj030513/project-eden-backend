package com.projecteden.dataset;

public record EvaluationCase(
		String caseId,
		VisionGroundTruth prediction,
		VisionGroundTruth groundTruth,
		VisionGroundTruth expected,
		EvaluationCaseStatus status) {
}
