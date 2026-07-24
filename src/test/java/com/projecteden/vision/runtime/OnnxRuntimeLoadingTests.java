package com.projecteden.vision.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OnnxRuntimeLoadingTests {

	@Test
	void loadsTheNativeRuntimeOrReportsAControlledUnavailableState() {
		var health = new OnnxVisionRuntime().health();

		assertThat(health.osName()).isNotBlank();
		assertThat(health.osArchitecture()).isNotBlank();
		assertThat(health.javaVersion()).isNotBlank();
		if (health.available()) {
			assertThat(health.runtimeVersion()).isNotBlank();
			assertThat(health.errorCode()).isNull();
		} else {
			assertThat(health.errorCode()).isNotNull();
		}
	}
}
