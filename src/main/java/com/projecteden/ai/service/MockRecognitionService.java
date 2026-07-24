package com.projecteden.ai.service;

import org.springframework.stereotype.Service;

import com.projecteden.ai.dto.RecognitionResult;
import com.projecteden.memorytaxonomy.observation.LegacyRecognitionProjection;
import com.projecteden.memorytaxonomy.observation.MockImageObservationProvider;
import com.projecteden.photo.domain.Photo;

@Service
public class MockRecognitionService implements RecognitionService {

	private final MockImageObservationProvider imageObservationProvider;
	private final LegacyRecognitionProjection projection;

	public MockRecognitionService(
			MockImageObservationProvider imageObservationProvider,
			LegacyRecognitionProjection projection) {
		this.imageObservationProvider = imageObservationProvider;
		this.projection = projection;
	}

	@Override
	public RecognitionResult recognize(Photo photo) {
		return projection.project(imageObservationProvider.observe(photo));
	}
}
