package com.projecteden.vision.detection;

public record DetectionConfidence(float value) {
	public DetectionConfidence {
		if (!Float.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Detection confidence must be between zero and one.");
	}
}
