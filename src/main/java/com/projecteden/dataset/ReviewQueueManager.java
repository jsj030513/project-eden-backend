package com.projecteden.dataset;

import java.util.List;
import java.util.Optional;

/** Filesystem-only review boundary; no entity, database write, controller, or public API. */
public interface ReviewQueueManager {
	ReviewItem enqueue(VisionDatasetId datasetId, VisionDatasetCaseId caseId, VisionGroundTruth prediction, String reviewer, String notes);
	Optional<ReviewItem> find(VisionDatasetId datasetId, String reviewId);
	List<ReviewItem> listPending(VisionDatasetId datasetId);
	ReviewItem approve(VisionDatasetId datasetId, String reviewId, String reviewer, String notes);
	ReviewItem correct(VisionDatasetId datasetId, String reviewId, VisionGroundTruth groundTruth, String reviewer, String notes);
	ReviewItem editGroundTruth(VisionDatasetId datasetId, String reviewId, GroundTruthPatch patch, String editedBy);
	ReviewItem reject(VisionDatasetId datasetId, String reviewId, String reviewer, String notes);
}
