package com.projecteden.vision.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecteden.vision.client.EdenVisionClient;

class EdenVisionClientConfigTests {
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
			.withUserConfiguration(EdenVisionClientConfig.class)
			.withBean(RestClient.Builder.class, RestClient::builder)
			.withBean(ObjectMapper.class, ObjectMapper::new);

	@Test
	void disabledByDefaultWithoutRequiringExternalUrl() {
		runner.run(context -> {
			assertThat(context).hasNotFailed().doesNotHaveBean(EdenVisionClient.class);
		});
	}

	@Test
	void bindsSeparateNamespaceAndAppliesBothTimeouts() {
		runner.withPropertyValues("eden.vision-api.enabled=true", "eden.vision-api.base-url=https://vision.example",
				"eden.vision-api.connect-timeout=2s", "eden.vision-api.read-timeout=7s",
				"eden.vision.enabled=false").run(context -> {
			assertThat(context).hasNotFailed().hasSingleBean(EdenVisionClient.class);
			var properties = context.getBean(EdenVisionApiProperties.class);
			assertThat(properties.getBaseUrl().toString()).isEqualTo("https://vision.example");
			assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
			assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(7));
			var factory = EdenVisionClientConfig.requestFactory(properties);
			assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(2000);
			assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(7000);
		});
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "relative", "ftp://vision.example", "http:///missing-host",
			"https://user:secret@vision.example", "https://vision.example?target=other",
			"https://vision.example#fragment", "https://vision.example:99999", "not a uri"})
	void rejectsInvalidBaseUrl(String url) {
		runner.withPropertyValues("eden.vision-api.enabled=true", "eden.vision-api.base-url=" + url)
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void requiresUrlWhenEnabled() {
		runner.withPropertyValues("eden.vision-api.enabled=true")
				.run(context -> assertThat(context).hasFailed());
	}

	@ParameterizedTest
	@ValueSource(strings = {"0ms", "-1s", "1ns", "2147483648ms"})
	void rejectsUnsafeTimeoutsForEitherSetting(String timeout) {
		for (String name : new String[] {"connect-timeout", "read-timeout"}) {
			runner.withPropertyValues("eden.vision-api.enabled=true", "eden.vision-api.base-url=https://vision.example",
					"eden.vision-api." + name + "=" + timeout)
					.run(context -> assertThat(context).hasFailed());
		}
	}
}
