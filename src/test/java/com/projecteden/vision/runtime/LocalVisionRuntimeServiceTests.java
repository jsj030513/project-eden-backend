package com.projecteden.vision.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.projecteden.vision.config.VisionModelProperties;
import com.projecteden.vision.yolox.YoloXImagePreprocessor;
import com.projecteden.vision.yolox.YoloXOutputDecoder;

class LocalVisionRuntimeServiceTests {

	@Test
	void keepsDisabledRuntimeUnavailableWithoutLoadingAModel() {
		VisionModelProperties properties = new VisionModelProperties();
		properties.setEnabled(false);

		LocalVisionRuntimeService runtime = runtime(properties);

		assertThat(runtime.ready()).isFalse();
		assertThat(runtime.status()).isEqualTo(LocalVisionRuntimeStatus.DISABLED);
		assertThat(runtime.unavailableReason()).isEqualTo("LOCAL_DISABLED");
	}

	@Test
	void cachesMissingModelReadinessResult() {
		VisionModelProperties properties = new VisionModelProperties();
		properties.setEnabled(true);

		LocalVisionRuntimeService runtime = runtime(properties);

		assertThat(runtime.ready()).isFalse();
		assertThat(runtime.ready()).isFalse();
		assertThat(runtime.status()).isEqualTo(LocalVisionRuntimeStatus.MODEL_MISSING);
	}

	@Test
	void rejectsChecksumMismatchBeforeCreatingAnOnnxSession(@TempDir Path tempDir) throws Exception {
		Path fakeModel = tempDir.resolve("not-a-model.onnx");
		Files.writeString(fakeModel, "not an onnx model");
		VisionModelProperties properties = new VisionModelProperties();
		properties.setEnabled(true);
		properties.getModel().setPath(fakeModel.toString());
		properties.getModel().setSha256("0".repeat(64));

		LocalVisionRuntimeService runtime = runtime(properties);

		assertThat(runtime.ready()).isFalse();
		assertThat(runtime.status()).isEqualTo(LocalVisionRuntimeStatus.CHECKSUM_MISMATCH);
	}

	private LocalVisionRuntimeService runtime(VisionModelProperties properties) {
		return new LocalVisionRuntimeService(properties, new YoloXImagePreprocessor(), new YoloXOutputDecoder());
	}
}
