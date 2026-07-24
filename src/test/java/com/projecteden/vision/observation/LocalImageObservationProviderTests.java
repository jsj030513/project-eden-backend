package com.projecteden.vision.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.projecteden.vision.detection.BoundingBox;
import com.projecteden.vision.detection.DetectionConfidence;
import com.projecteden.vision.detection.DetectionObject;
import com.projecteden.vision.detection.DetectionResult;
import com.projecteden.memorytaxonomy.observation.ImageObservationRequest;
import com.projecteden.vision.runtime.LocalVisionRuntime;

class LocalImageObservationProviderTests {
	@Test void convertsYoloDetectionThroughTheObservationBuilder() {
		var result = new DetectionResult(List.of(new DetectionObject("CAT",new DetectionConfidence(.9f),new BoundingBox(1,2,10,12))),"yolox-nano-0.1.1rc0");
		var provider = provider(result);
		assertThat(provider.fromDetections(result).objects()).containsExactly("CAT");
		assertThat(provider.observe(ImageObservationRequest.of(1L, "photo.jpg", "image/jpeg", 1, new byte[] {1})).objects()).containsExactly("CAT");
	}

	@Test void reportsAnExplicitCodeWhenTheLocalRuntimeIsUnavailable() {
		var provider = provider(new RuntimeException("unavailable"));

		assertThatThrownBy(() -> provider.observe((ImageObservationRequest) null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("LOCAL_PROVIDER_DISABLED");
	}

	private LocalImageObservationProvider provider(DetectionResult detectionResult) {
		return provider(new LocalVisionRuntime() {
			@Override public DetectionResult detect(ImageObservationRequest request) { return detectionResult; }
			@Override public boolean ready() { return true; }
			@Override public String modelVersion() { return detectionResult.modelVersion(); }
		});
	}

	private LocalImageObservationProvider provider(RuntimeException exception) {
		return provider(new LocalVisionRuntime() {
			@Override public DetectionResult detect(ImageObservationRequest request) { throw exception; }
			@Override public boolean ready() { return false; }
			@Override public String modelVersion() { return "yolox-nano"; }
		});
	}

	private LocalImageObservationProvider provider(LocalVisionRuntime runtime) {
		return new LocalImageObservationProvider(
				new DetectionObservationBuilder(new DetectionSceneResolver(),new DetectionSubjectResolver(),new DetectionObjectResolver()), runtime);
	}
}
