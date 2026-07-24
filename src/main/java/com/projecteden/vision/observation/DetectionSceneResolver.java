package com.projecteden.vision.observation;

import org.springframework.stereotype.Component;

import com.projecteden.vision.detection.DetectionResult;

@Component
public class DetectionSceneResolver {
	public String resolve(DetectionResult result) {
		// COCO-80 YOLOX does not provide sufficiently reliable scene evidence for Eden.
		// Keep scene unknown rather than inferring indoor/outdoor from object co-occurrence.
		return null;
	}
}
