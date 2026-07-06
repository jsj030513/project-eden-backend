package com.projecteden.ai.service;

import org.springframework.stereotype.Service;

import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.ai.dto.RecognitionResult;
import com.projecteden.photo.domain.Photo;

@Service
public class MockRecognitionService implements RecognitionService {

	@Override
	public RecognitionResult recognize(Photo photo) {
		// TODO: Gemini, OpenAI Vision 또는 다른 Vision API 구현체로 교체한다.
		return new RecognitionResult(RecognizedObject.FLOWER, 95, true);
	}
}
