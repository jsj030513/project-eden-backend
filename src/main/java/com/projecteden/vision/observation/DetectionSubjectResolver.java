package com.projecteden.vision.observation;

import java.util.List;

import org.springframework.stereotype.Component;

import com.projecteden.vision.detection.DetectionResult;

@Component
public class DetectionSubjectResolver {
	public List<String> resolve(DetectionResult result) {
		return result.objects().stream().anyMatch(object -> object.code().equals("PERSON")) ? List.of("PERSON") : List.of();
	}
}
