package com.projecteden.vision.observation;

import org.springframework.stereotype.Component;

import com.projecteden.memorytaxonomy.observation.ImageObservation;
import com.projecteden.memorytaxonomy.observation.ImageObservationProvider;
import com.projecteden.memorytaxonomy.observation.ImageObservationRequest;
import com.projecteden.vision.detection.DetectionResult;
import com.projecteden.vision.runtime.LocalVisionRuntime;

/**
 * The local model is opt-in through the resolver. The provider never normalizes
 * source images itself and safely returns UNKNOWN fallback when the local runtime
 * cannot produce detections.
 */
@Component
public class LocalImageObservationProvider implements ImageObservationProvider {
	private final DetectionObservationBuilder observationBuilder;
	private final LocalVisionRuntime localVisionRuntime;

	public LocalImageObservationProvider(DetectionObservationBuilder observationBuilder, LocalVisionRuntime localVisionRuntime) {
		this.observationBuilder = observationBuilder;
		this.localVisionRuntime = localVisionRuntime;
	}

	@Override
	public ImageObservation observe(ImageObservationRequest request) {
		if (!localVisionRuntime.ready()) {
			throw new IllegalArgumentException(unavailableCode(localVisionRuntime.unavailableReason()));
		}
		try {
			DetectionResult result = localVisionRuntime.detect(request);
			return fromDetections(result);
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException("PLATFORM_UNVERIFIED", exception);
		}
	}

	private String unavailableCode(String reason) {
		if ("LOCAL_DISABLED".equals(reason) || "LOCAL_PROVIDER_DISABLED".equals(reason)) return "LOCAL_PROVIDER_DISABLED";
		if ("LOCAL_MODEL_MISSING".equals(reason) || "LOCAL_CHECKSUM_MISMATCH".equals(reason)
				|| "LOCAL_MODEL_LOAD_FAILED".equals(reason)) return "MODEL_NOT_READY";
		return "PLATFORM_UNVERIFIED";
	}
	public ImageObservation fromDetections(DetectionResult result) { return observationBuilder.build(result); }
	@Override public String provider() { return DetectionObservationBuilder.PROVIDER; }
	@Override public String modelVersion() { return localVisionRuntime.modelVersion(); }
}
