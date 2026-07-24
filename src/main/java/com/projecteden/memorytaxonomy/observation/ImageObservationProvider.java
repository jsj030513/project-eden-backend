package com.projecteden.memorytaxonomy.observation;

import com.projecteden.photo.domain.Photo;

public interface ImageObservationProvider {

	ImageObservation observe(ImageObservationRequest request);

	default ImageObservation observe(Photo photo) {
		return observe(ImageObservationRequest.from(photo));
	}

	String provider();

	String modelVersion();
}
