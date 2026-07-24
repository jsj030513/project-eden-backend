package com.projecteden.vision.observation;

import java.util.List;

import org.springframework.stereotype.Component;

import com.projecteden.vision.detection.DetectionResult;

@Component
public class DetectionObjectResolver {
	public List<String> resolve(DetectionResult result) {
		return result.objects().stream().map(object -> object.code()).distinct().toList();
	}
}
