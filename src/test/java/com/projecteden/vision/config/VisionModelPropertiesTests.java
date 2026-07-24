package com.projecteden.vision.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VisionModelPropertiesTests {

	@Test
	void defaultsKeepVisionDisabledAndRequireNoModelPath() {
		VisionModelProperties properties = new VisionModelProperties();

		assertThat(properties.isEnabled()).isFalse();
		assertThat(properties.getRuntime()).isEqualTo("onnx");
		assertThat(properties.getYolox().getConfidenceThreshold()).isEqualTo(.20f);
		assertThat(properties.getModel().getType()).isEqualTo("yolox-nano");
		assertThat(properties.getModel().getPath()).isBlank();
		assertThat(properties.getModel().getSha256()).isBlank();
	}
}
