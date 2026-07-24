package com.projecteden.vision.detection;

import java.util.LinkedHashSet;
import java.util.List;

public record DetectionResult(List<DetectionObject> objects, String modelVersion) {
	public DetectionResult {
		objects = objects == null ? List.of() : List.copyOf(new LinkedHashSet<>(objects));
		modelVersion = modelVersion == null || modelVersion.isBlank() ? "yolox-nano" : modelVersion;
	}
	public boolean isEmpty() { return objects.isEmpty(); }
}
