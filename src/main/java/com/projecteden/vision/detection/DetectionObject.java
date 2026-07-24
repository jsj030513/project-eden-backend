package com.projecteden.vision.detection;

import java.util.Locale;

public record DetectionObject(String code, DetectionConfidence confidence, BoundingBox boundingBox) {
	public DetectionObject {
		if (code == null || code.isBlank()) throw new IllegalArgumentException("Detection object code is required.");
		code = code.trim().toUpperCase(Locale.ROOT);
	}
}
