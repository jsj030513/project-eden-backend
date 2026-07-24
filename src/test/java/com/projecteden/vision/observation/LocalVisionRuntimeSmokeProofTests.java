package com.projecteden.vision.observation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.projecteden.imagenormalization.DefaultImageNormalizationService;
import com.projecteden.imagenormalization.ImageFormatDetector;
import com.projecteden.imagenormalization.ImageNormalizationProperties;
import com.projecteden.memorytaxonomy.observation.ImageObservationRequest;
import com.projecteden.memorytaxonomy.observation.UploadedImagePayload;
import com.projecteden.vision.config.VisionModelProperties;
import com.projecteden.vision.runtime.LocalVisionRuntimeService;
import com.projecteden.vision.yolox.YoloXImagePreprocessor;
import com.projecteden.vision.yolox.YoloXOutputDecoder;

/** Opt-in local proof: this validates inference execution, not a specific label. */
@EnabledIfSystemProperty(named = "eden.vision.proof.enabled", matches = "true")
class LocalVisionRuntimeSmokeProofTests {

	@Test
	void loadsTheConfiguredModelAndExecutesInferenceForTheProvidedImage() throws Exception {
		Path imagePath = Path.of(requiredEnv("EDEN_VISION_PROOF_IMAGE_PATH"));
		byte[] source = Files.readAllBytes(imagePath);
		var normalized = new DefaultImageNormalizationService(new ImageFormatDetector(), new ImageNormalizationProperties())
				.normalize(UploadedImagePayload.of(imagePath.getFileName().toString(), "image/jpeg", source.length, source));
		VisionModelProperties properties = new VisionModelProperties();
		properties.setEnabled(true);
		properties.getModel().setPath(requiredEnv("EDEN_VISION_MODEL_PATH"));
		properties.getModel().setSha256(requiredEnv("EDEN_VISION_MODEL_SHA256"));
		LocalVisionRuntimeService runtime = new LocalVisionRuntimeService(
				properties, new YoloXImagePreprocessor(), new YoloXOutputDecoder());
		try {
			assertThat(runtime.ready()).isTrue();
			var result = runtime.detect(ImageObservationRequest.normalized(1L, imagePath.getFileName().toString(), normalized));
			assertThat(result).isNotNull();
			assertThat(result.modelVersion()).isEqualTo("yolox-nano");
		} finally {
			runtime.close();
		}
	}

	private String requiredEnv(String name) {
		String value = System.getenv(name);
		if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required for the opt-in proof.");
		return value;
	}
}
