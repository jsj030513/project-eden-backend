package com.projecteden.vision.yolox;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "eden.vision.proof.enabled", matches = "true")
class YoloXInferenceProofTests {

	@Test
	void runsOfficialYoloXNanoTwentyTimesDeterministically() throws Exception {
		Path model = Path.of(requiredEnv("EDEN_VISION_MODEL_PATH"));
		String sha256 = requiredEnv("EDEN_VISION_MODEL_SHA256");
		BufferedImage image = ImageIO.read(Path.of(requiredEnv("EDEN_VISION_PROOF_IMAGE_PATH")).toFile());
		YoloXPreprocessResult input = new YoloXImagePreprocessor().preprocess(image);
		YoloXOutputDecoder decoder = new YoloXOutputDecoder();
		long loadStart = System.nanoTime();
		try (YoloXInferenceEngine engine = new YoloXInferenceEngine(model, sha256)) {
			long loaded = System.nanoTime();
			long firstStart = System.nanoTime();
			float[][] firstRaw = engine.run(input);
			long firstEnd = System.nanoTime();
			assertFinite(firstRaw);
			List<YoloXDetection> baseline = decoder.decode(firstRaw, input.transform(), .30f, .45f, 100);
			List<Long> warmNanos = new ArrayList<>();
			for (int run = 0; run < 20; run++) {
				long start = System.nanoTime();
				float[][] raw = engine.run(input);
				warmNanos.add(System.nanoTime() - start);
				assertFinite(raw);
				List<YoloXDetection> detections = decoder.decode(raw, input.transform(), .30f, .45f, 100);
				assertThat(detections).isEqualTo(baseline);
			}
			assertThat(baseline).anyMatch(detection -> detection.className().equals("cat"));
			YoloXDetection cat = baseline.stream().filter(detection -> detection.className().equals("cat")).findFirst().orElseThrow();
			float[][] blank = engine.run(new YoloXImagePreprocessor().preprocess(new BufferedImage(416, 416, BufferedImage.TYPE_INT_RGB)));
			assertFinite(blank);
			warmNanos.sort(Long::compareTo);
			System.out.printf("YOLOX proof modelLoadMs=%.1f firstMs=%.1f warmMinMs=%.1f warmMedianMs=%.1f warmP95Ms=%.1f warmMaxMs=%.1f detections=%d catConfidence=%.6f catBox=[%.1f,%.1f,%.1f,%.1f]%n",
					(loaded-loadStart)/1_000_000d, (firstEnd-firstStart)/1_000_000d, warmNanos.getFirst()/1_000_000d,
					warmNanos.get(warmNanos.size()/2)/1_000_000d, warmNanos.get((int)Math.ceil(warmNanos.size()*.95)-1)/1_000_000d,
					warmNanos.getLast()/1_000_000d, baseline.size(), cat.confidence(), cat.x1(), cat.y1(), cat.x2(), cat.y2());
		}
	}

	private String requiredEnv(String name) {
		String value = System.getenv(name);
		if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required for the opt-in proof.");
		return value;
	}

	private void assertFinite(float[][] output) {
		assertThat(output.length).isEqualTo(3549);
		for (float[] row : output) {
			for (float value : row) {
				assertThat(Float.isFinite(value)).isTrue();
			}
		}
	}
}
