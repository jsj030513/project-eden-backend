package com.projecteden.ai.dto;

import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.village.domain.VillageCategory;
import com.projecteden.world.ecology.WorldChangeResult;

public record RecognitionResponse(
		Long id,
		Long photoId,
		RecognizedObject recognizedObject,
		VillageCategory category,
		int confidence,
		boolean recognized,
		boolean fallback,
		WorldChangeResult worldChange) {
}
