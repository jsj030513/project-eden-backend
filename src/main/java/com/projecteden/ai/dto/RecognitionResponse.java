package com.projecteden.ai.dto;

import com.projecteden.ai.domain.RecognizedObject;

public record RecognitionResponse(
		Long id,
		Long photoId,
		RecognizedObject recognizedObject,
		int confidence,
		boolean recognized) {
}
