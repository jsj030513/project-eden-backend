package com.projecteden.memorytaxonomy.observation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.projecteden.memorytaxonomy.observation.openai.OpenAIObservationClient;
import com.projecteden.memorytaxonomy.observation.openai.OpenAIObservationProperties;
import com.projecteden.vision.observation.DetectionObjectResolver;
import com.projecteden.vision.observation.DetectionObservationBuilder;
import com.projecteden.vision.observation.DetectionSceneResolver;
import com.projecteden.vision.observation.DetectionSubjectResolver;
import com.projecteden.vision.observation.LocalImageObservationProvider;
import com.projecteden.vision.runtime.LocalVisionRuntime;

class ImageObservationProviderResolverTests {

	private final MockImageObservationProvider mock = new MockImageObservationProvider();
	private final OpenAIObservationClient client = request -> null;
	private final LocalImageObservationProvider local = new LocalImageObservationProvider(
			new DetectionObservationBuilder(new DetectionSceneResolver(), new DetectionSubjectResolver(), new DetectionObjectResolver()),
			new LocalVisionRuntime() {
				@Override public com.projecteden.vision.detection.DetectionResult detect(ImageObservationRequest request) { return new com.projecteden.vision.detection.DetectionResult(java.util.List.of(), "yolox-nano"); }
				@Override public boolean ready() { return false; }
				@Override public String modelVersion() { return "yolox-nano"; }
			});

	@Test
	void resolvesMockByDefault() {
		ImageObservationProviderResolver resolver = resolver(properties("mock", "", ""));

		assertThat(resolver.resolve()).isSameAs(mock);
	}

	@Test
	void resolvesOpenAIWhenConfigured() {
		OpenAIObservationProperties properties = properties("openai", "test-key", "test-model");
		OpenAIImageObservationProvider openai = new OpenAIImageObservationProvider(properties, client, mock);
		ImageObservationProviderResolver resolver =
				new ImageObservationProviderResolver(properties, mock, openai, local);

		assertThat(resolver.resolve()).isSameAs(openai);
	}

	@Test
	void resolvesLocalOnlyWhenExplicitlySelected() {
		assertThat(resolver(properties("local", "", "")).resolve()).isSameAs(local);
	}

	@Test
	void fallsBackToMockWhenOpenAIConfigurationIsMissing() {
		assertThat(resolver(properties("openai", "", "test-model")).resolve()).isSameAs(mock);
		assertThat(resolver(properties("openai", "test-key", "")).resolve()).isSameAs(mock);
	}

	@Test
	void fallsBackToMockWhenProviderIsUnknown() {
		ImageObservationProviderResolver resolver = resolver(properties("something-else", "", ""));

		assertThat(resolver.resolve()).isSameAs(mock);
	}

	private ImageObservationProviderResolver resolver(OpenAIObservationProperties properties) {
		return new ImageObservationProviderResolver(
				properties,
				mock,
				new OpenAIImageObservationProvider(properties, client, mock), local);
	}

	private OpenAIObservationProperties properties(String provider, String apiKey, String model) {
		OpenAIObservationProperties properties = new OpenAIObservationProperties();
		properties.setProvider(provider);
		properties.getOpenai().setApiKey(apiKey);
		properties.getOpenai().setModel(model);
		return properties;
	}
}
