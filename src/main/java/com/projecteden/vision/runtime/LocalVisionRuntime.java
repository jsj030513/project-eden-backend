package com.projecteden.vision.runtime;

import com.projecteden.memorytaxonomy.observation.ImageObservationRequest;
import com.projecteden.vision.detection.DetectionResult;

/** Internal local-model boundary used by the observation provider. */
public interface LocalVisionRuntime {
	DetectionResult detect(ImageObservationRequest request);

	boolean ready();

	default String unavailableReason() {
		return "LOCAL_PROVIDER_DISABLED";
	}

	String modelVersion();
}
