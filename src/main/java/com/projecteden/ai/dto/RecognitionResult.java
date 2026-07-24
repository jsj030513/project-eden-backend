package com.projecteden.ai.dto;

import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.village.domain.VillageCategory;

public record RecognitionResult(
		RecognizedObject recognizedObject,
		VillageCategory category,
		int confidence,
		boolean recognized,
		boolean fallback) {

	public static RecognitionResult recognized(RecognizedObject recognizedObject, int confidence) {
		return new RecognitionResult(
				recognizedObject,
				recognizedObject.getCategory(),
				confidence,
				true,
				false);
	}

	public static RecognitionResult generalMemory() {
		return new RecognitionResult(
				RecognizedObject.OBJECT,
				RecognizedObject.OBJECT.getCategory(),
				0,
				true,
				true);
	}

	public static RecognitionResult unknown() {
		return new RecognitionResult(
				RecognizedObject.UNKNOWN,
				VillageCategory.UNKNOWN,
				0,
				false,
				true);
	}
}
