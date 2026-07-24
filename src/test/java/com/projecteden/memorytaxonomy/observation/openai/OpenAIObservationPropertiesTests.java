package com.projecteden.memorytaxonomy.observation.openai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenAIObservationPropertiesTests {

	@Test
	void hasSafeDefaults() {
		OpenAIObservationProperties properties = new OpenAIObservationProperties();

		assertThat(properties.getProvider()).isEqualTo("mock");
		assertThat(properties.getOpenai().getApiKey()).isBlank();
		assertThat(properties.getOpenai().getModel()).isBlank();
		assertThat(properties.getOpenai().getBaseUrl()).isEqualTo("https://api.openai.com/v1");
		assertThat(properties.getOpenai().getConnectTimeoutMs()).isEqualTo(3000);
		assertThat(properties.getOpenai().getReadTimeoutMs()).isEqualTo(15000);
		assertThat(properties.getOpenai().getMaxRetries()).isEqualTo(1);
		assertThat(properties.getOpenai().getImageDetail()).isEqualTo("auto");
		assertThat(properties.isOpenAIConfigured()).isFalse();
	}

	@Test
	void requiresBothApiKeyAndModel() {
		OpenAIObservationProperties properties = new OpenAIObservationProperties();
		properties.getOpenai().setApiKey("test-key");
		assertThat(properties.isOpenAIConfigured()).isFalse();

		properties.getOpenai().setModel("test-model");
		assertThat(properties.isOpenAIConfigured()).isTrue();
	}
}
