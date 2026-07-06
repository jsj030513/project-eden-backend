package com.projecteden.ai.service;

import com.projecteden.ai.dto.RecognitionResult;
import com.projecteden.photo.domain.Photo;

public interface RecognitionService {

	RecognitionResult recognize(Photo photo);
}
