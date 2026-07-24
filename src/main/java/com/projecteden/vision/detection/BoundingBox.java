package com.projecteden.vision.detection;

public record BoundingBox(float x1, float y1, float x2, float y2) {
	public BoundingBox {
		if (!Float.isFinite(x1) || !Float.isFinite(y1) || !Float.isFinite(x2) || !Float.isFinite(y2) || x2 <= x1 || y2 <= y1) {
			throw new IllegalArgumentException("Bounding box coordinates must be finite and ordered.");
		}
	}
}
