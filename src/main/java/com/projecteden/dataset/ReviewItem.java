package com.projecteden.dataset;

import java.time.Instant;
import java.util.List;

public record ReviewItem(
		String schemaVersion,
		String reviewId,
		VisionDatasetId datasetId,
		VisionDatasetCaseId caseId,
		Instant createdAt,
		Instant updatedAt,
		ReviewStatus status,
		VisionGroundTruth prediction,
		VisionGroundTruth groundTruth,
		String reviewer,
		String notes,
		List<GroundTruthEditResult> history) {

	public ReviewItem {
		schemaVersion = schemaVersion == null ? "eden-review-schema-v1" : schemaVersion;
		if (reviewId == null || !reviewId.matches("[a-z0-9][a-z0-9-]{0,63}")) {
			throw new IllegalArgumentException("INVALID_REVIEW_ID");
		}
		if (datasetId == null || caseId == null || status == null || prediction == null || createdAt == null || updatedAt == null) {
			throw new IllegalArgumentException("INVALID_REVIEW_ITEM");
		}
		history = history == null ? List.of() : List.copyOf(history);
	}

	public ReviewItem(String schemaVersion, String reviewId, VisionDatasetId datasetId, VisionDatasetCaseId caseId, Instant createdAt,
			Instant updatedAt, ReviewStatus status, VisionGroundTruth prediction, VisionGroundTruth groundTruth, String reviewer, String notes) {
		this(schemaVersion, reviewId, datasetId, caseId, createdAt, updatedAt, status, prediction, groundTruth, reviewer, notes, List.of());
	}

	public boolean publishesGroundTruth() {
		return (status == ReviewStatus.APPROVED || status == ReviewStatus.CORRECTED) && groundTruth != null;
	}
}
