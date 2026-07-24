package com.projecteden.dataset;

import java.time.Instant;

public record GroundTruthEditResult(
		ReviewDecision decision,
		VisionGroundTruth previousGroundTruth,
		VisionGroundTruth groundTruth,
		Instant editedAt,
		String editedBy,
		String notes) {
}
