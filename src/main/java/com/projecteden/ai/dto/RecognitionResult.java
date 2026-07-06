package com.projecteden.ai.dto;

import com.projecteden.ai.domain.RecognizedObject;

public record RecognitionResult(
		RecognizedObject recognizedObject,
		int confidence,
		boolean recognized) {
}
