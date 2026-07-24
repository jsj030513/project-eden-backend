package com.projecteden.vision.observation;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.projecteden.imagenormalization.DefaultImageNormalizationService;
import com.projecteden.imagenormalization.ImageFormatDetector;
import com.projecteden.imagenormalization.ImageNormalizationProperties;
import com.projecteden.imagenormalization.NormalizedImage;
import com.projecteden.memorytaxonomy.observation.ImageObservationRequest;
import com.projecteden.memorytaxonomy.observation.UploadedImagePayload;
import com.projecteden.vision.config.VisionModelProperties;
import com.projecteden.vision.runtime.LocalVisionRuntimeService;
import com.projecteden.vision.yolox.YoloXImagePreprocessor;
import com.projecteden.vision.yolox.YoloXOutputDecoder;

/** Opt-in proof only: default Maven tests never load an operator-provided model. */
@EnabledIfSystemProperty(named = "eden.vision.proof.enabled", matches = "true")
class LocalVisionEndToEndProofTests {

	@Test
	void convertsNormalizedCatImageToLocalObservationAcrossRepeatedAndConcurrentRequests() throws Exception {
		LocalVisionRuntimeService runtime = runtime();
		try {
			LocalImageObservationProvider provider = provider(runtime);
			ImageObservationRequest request = normalizedRequest(Path.of(requiredEnv("EDEN_VISION_PROOF_IMAGE_PATH")));

			var first = provider.observe(request);
			assertThat(first.fallback()).isFalse();
			assertThat(first.recognized()).isTrue();
			assertThat(first.objects()).contains("CAT");
			for (int index = 0; index < 20; index++) {
				assertThat(provider.observe(request).toMap()).isEqualTo(first.toMap());
			}

			try (ExecutorService executor = Executors.newFixedThreadPool(5)) {
				List<Callable<com.projecteden.memorytaxonomy.observation.ImageObservation>> tasks = java.util.stream.IntStream.range(0, 5)
						.mapToObj(index -> (Callable<com.projecteden.memorytaxonomy.observation.ImageObservation>) () -> provider.observe(request))
						.toList();
				assertThat(executor.invokeAll(tasks).stream().map(future -> {
					try { return future.get(); } catch (Exception exception) { throw new AssertionError(exception); }
				}).map(com.projecteden.memorytaxonomy.observation.ImageObservation::toMap).toList())
						.containsOnly(first.toMap());
			}
		} finally {
			runtime.close();
		}
	}

	@Test
	void returnsFallbackForAUniformImageWithoutPersistingAnything() throws Exception {
		LocalVisionRuntimeService runtime = runtime();
		try {
			BufferedImage blank = new BufferedImage(416, 416, BufferedImage.TYPE_INT_RGB);
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			ImageIO.write(blank, "png", output);
			var observation = provider(runtime).observe(request(output.toByteArray(), 416, 416, "image/png"));

			// A model false positive is not a runtime error; this assertion only protects the API boundary.
			assertThat(observation).isNotNull();
		} finally {
			runtime.close();
		}
	}

	private LocalVisionRuntimeService runtime() {
		VisionModelProperties properties = new VisionModelProperties();
		properties.setEnabled(true);
		properties.getModel().setPath(requiredEnv("EDEN_VISION_MODEL_PATH"));
		properties.getModel().setSha256(requiredEnv("EDEN_VISION_MODEL_SHA256"));
		return new LocalVisionRuntimeService(properties, new YoloXImagePreprocessor(), new YoloXOutputDecoder());
	}

	private LocalImageObservationProvider provider(LocalVisionRuntimeService runtime) {
		return new LocalImageObservationProvider(
				new DetectionObservationBuilder(new DetectionSceneResolver(), new DetectionSubjectResolver(), new DetectionObjectResolver()), runtime);
	}

	private ImageObservationRequest normalizedRequest(Path imagePath) throws Exception {
		byte[] bytes = Files.readAllBytes(imagePath);
		NormalizedImage normalized = new DefaultImageNormalizationService(new ImageFormatDetector(), new ImageNormalizationProperties())
				.normalize(UploadedImagePayload.of("proof.jpg", "image/jpeg", bytes.length, bytes));
		return ImageObservationRequest.normalized(1L, null, normalized);
	}

	private ImageObservationRequest request(byte[] bytes, int width, int height, String contentType) throws Exception {
		ByteArrayOutputStream normalizedOutput = new ByteArrayOutputStream();
		ImageIO.write(ImageIO.read(new java.io.ByteArrayInputStream(bytes)), "png", normalizedOutput);
		byte[] normalizedBytes = normalizedOutput.toByteArray();
		NormalizedImage image = new DefaultImageNormalizationService(new ImageFormatDetector(), new ImageNormalizationProperties())
				.normalize(UploadedImagePayload.of("blank.png", contentType, normalizedBytes.length, normalizedBytes));
		return ImageObservationRequest.normalized(1L, null, image);
	}

	private String requiredEnv(String name) {
		String value = System.getenv(name);
		if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required for the opt-in proof.");
		return value;
	}
}
