package com.projecteden.vision.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.projecteden.vision.VisionRuntimeException;

class VisionModelIntegrityTests {

	@TempDir Path tempDirectory;

	@Test
	void streamsAStableSha256WithoutLoadingTheWholeFileAsAString() throws Exception {
		Path model = tempDirectory.resolve("model.onnx");
		Files.writeString(model, "eden-vision-proof");

		assertThat(VisionModelIntegrity.sha256(model))
				.isEqualTo("a5058fc276a8a8fb987d00dd9b8361a351ad4e48cc165023d61d702a0ec7c061");
	}

	@Test
	void reportsMissingModelAsTypedFailure() {
		assertThatThrownBy(() -> VisionModelIntegrity.sha256(tempDirectory.resolve("missing.onnx")))
				.isInstanceOf(VisionRuntimeException.class);
	}
}
