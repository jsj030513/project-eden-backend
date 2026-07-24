package com.projecteden.memorytaxonomy.observation;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.projecteden.memorytaxonomy.observation.openai.OpenAIObservationProperties;
import com.projecteden.vision.observation.LocalImageObservationProvider;

@Component
public class ImageObservationProviderResolver {

	private static final Logger log = LoggerFactory.getLogger(ImageObservationProviderResolver.class);

	private final OpenAIObservationProperties properties;
	private final MockImageObservationProvider mockProvider;
	private final OpenAIImageObservationProvider openAIProvider;
	private final LocalImageObservationProvider localProvider;

	public ImageObservationProviderResolver(
			OpenAIObservationProperties properties,
			MockImageObservationProvider mockProvider,
			OpenAIImageObservationProvider openAIProvider,
			LocalImageObservationProvider localProvider) {
		this.properties = properties;
		this.mockProvider = mockProvider;
		this.openAIProvider = openAIProvider;
		this.localProvider = localProvider;
	}

	public ImageObservationProvider resolve() {
		String provider = properties.getProvider() == null
				? "mock"
				: properties.getProvider().trim().toLowerCase(Locale.ROOT);
		return switch (provider) {
			case "mock", "" -> mockProvider;
			case "openai" -> resolveOpenAI();
			case "local" -> localProvider;
			default -> {
				log.warn("Unknown image observation provider '{}'. Falling back to mock provider.", provider);
				yield mockProvider;
			}
		};
	}

	private ImageObservationProvider resolveOpenAI() {
		if (!properties.isOpenAIConfigured()) {
			log.warn("OpenAI image observation provider selected but configuration is incomplete. Falling back to mock provider.");
			return mockProvider;
		}
		return openAIProvider;
	}
}
