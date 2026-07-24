package com.projecteden.dataset;

import java.time.Instant;
import java.util.List;

public final class GroundTruthEditor {
	private final GroundTruthTaxonomyValidator validator = new GroundTruthTaxonomyValidator();

	public GroundTruthEditResult edit(ReviewItem review, GroundTruthPatch patch, String editedBy) {
		if (review.status() != ReviewStatus.APPROVED && review.status() != ReviewStatus.CORRECTED) throw new IllegalArgumentException("GROUND_TRUTH_EDIT_NOT_ALLOWED");
		if (review.groundTruth() == null) throw new IllegalArgumentException("GROUND_TRUTH_NOT_FOUND");
		validator.validatePatch(patch);
		VisionGroundTruth current = review.groundTruth();
		VisionGroundTruth edited = new VisionGroundTruth(
				values(patch.objects(), current.objects()),
				values(patch.activities(), current.activities()),
				values(patch.relationships(), current.relationships()),
				code(patch.category(), current.category()),
				values(patch.secondaryCategories(), current.secondaryCategories()),
				values(patch.tags(), current.tags()),
				patch.fallback() == null ? current.fallback() : patch.fallback());
		validator.validateGroundTruth(edited);
		return new GroundTruthEditResult(ReviewDecision.CORRECT, current, edited, Instant.now(), editedBy, patch.notes());
	}

	public GroundTruthEditResult correct(ReviewItem review, VisionGroundTruth groundTruth, String editedBy, String notes) {
		if (review.status() != ReviewStatus.APPROVED && review.status() != ReviewStatus.CORRECTED) throw new IllegalArgumentException("GROUND_TRUTH_EDIT_NOT_ALLOWED");
		validator.validateGroundTruth(groundTruth);
		return new GroundTruthEditResult(ReviewDecision.CORRECT, review.groundTruth(), groundTruth, Instant.now(), editedBy, notes);
	}

	GroundTruthEditResult approved(VisionGroundTruth groundTruth, String editedBy, String notes) {
		validator.validateGroundTruth(groundTruth);
		return new GroundTruthEditResult(ReviewDecision.APPROVE, null, groundTruth, Instant.now(), editedBy, notes);
	}

	private String code(String candidate, String current) {
		if (candidate == null) return current;
		if (candidate.isBlank()) throw new IllegalArgumentException("INVALID_TAXONOMY_CATEGORY");
		return candidate.trim().toUpperCase();
	}

	private List<String> values(List<String> candidate, List<String> current) {
		return candidate == null ? current : candidate;
	}
}
