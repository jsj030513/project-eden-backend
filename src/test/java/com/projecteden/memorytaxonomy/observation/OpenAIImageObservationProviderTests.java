package com.projecteden.memorytaxonomy.observation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.projecteden.memorytaxonomy.observation.openai.OpenAIObservationClient;
import com.projecteden.memorytaxonomy.observation.openai.OpenAIObservationException;
import com.projecteden.memorytaxonomy.observation.openai.OpenAIObservationProperties;
import com.projecteden.memorytaxonomy.observation.openai.OpenAIObservationResponse;

class OpenAIImageObservationProviderTests {

	private final MockImageObservationProvider mock = new MockImageObservationProvider();

	@Test
	void convertsSuccessfulOpenAIResponseToImageObservation() {
		OpenAIImageObservationProvider provider = provider(request -> new OpenAIObservationResponse(
				true,
				BigDecimal.valueOf(0.91),
				List.of("dog", "DOG"),
				List.of("leash", "tree"),
				"park",
				List.of("walking"),
				List.of("friends"),
				List.of("warm", "calm")));

		ImageObservation observation = provider.observe(jpegRequest("dog-park.jpg"));

		assertThat(observation.provider()).isEqualTo("OPENAI");
		assertThat(observation.modelVersion()).isEqualTo("test-vision-model");
		assertThat(observation.subjects()).containsExactly("DOG");
		assertThat(observation.objects()).containsExactly("LEASH", "TREE");
		assertThat(observation.scene()).isEqualTo("PARK");
		assertThat(observation.activities()).containsExactly("WALKING");
		assertThat(observation.relationships()).containsExactly("FRIENDS");
		assertThat(observation.moodSignals()).containsExactly("WARM", "CALM");
		assertThat(observation.confidence()).isEqualByComparingTo("0.91");
		assertThat(observation.recognized()).isTrue();
		assertThat(observation.fallback()).isFalse();
	}

	@Test
	void supportedMimeTypesCanUseOpenAIProvider() {
		AtomicInteger calls = new AtomicInteger();
		OpenAIImageObservationProvider provider = provider(request -> {
			calls.incrementAndGet();
			return new OpenAIObservationResponse(
					true,
					BigDecimal.valueOf(0.9),
					List.of("DOG"),
					List.of(),
					null,
					List.of(),
					List.of(),
					List.of());
		});

		for (String contentType : List.of("image/jpeg", "image/png", "image/webp", "image/gif")) {
			ImageObservation observation = provider.observe(ImageObservationRequest.of(
					1L,
					"dog",
					contentType,
					10,
					"fake-image".getBytes()));

			assertThat(observation.provider()).isEqualTo("OPENAI");
			assertThat(observation.subjects()).containsExactly("DOG");
		}
		assertThat(calls).hasValue(4);
	}

	@Test
	void limitsAndNormalizesSignals() {
		List<String> manyObjects = new ArrayList<>();
		for (int i = 0; i < 25; i++) {
			manyObjects.add("object-" + i);
		}
		OpenAIImageObservationProvider provider = provider(request -> new OpenAIObservationResponse(
				true,
				BigDecimal.valueOf(0.8),
				List.of(" cat ", "bad/value", "cat"),
				manyObjects,
				" flower field ",
				List.of("walking-fast"),
				List.of(),
				List.of()));

		ImageObservation observation = provider.observe(jpegRequest("cat.jpg"));

		assertThat(observation.subjects()).containsExactly("CAT", "BADVALUE");
		assertThat(observation.objects()).hasSize(20);
		assertThat(observation.objects().getFirst()).isEqualTo("OBJECT_0");
		assertThat(observation.scene()).isEqualTo("FLOWER_FIELD");
		assertThat(observation.activities()).containsExactly("WALKING_FAST");
	}

	@Test
	void unknownResponseBecomesFallbackObservation() {
		OpenAIImageObservationProvider provider = provider(request -> new OpenAIObservationResponse(
				false,
				BigDecimal.valueOf(0.2),
				List.of(),
				List.of(),
				null,
				List.of(),
				List.of(),
				List.of()));

		ImageObservation observation = provider.observe(jpegRequest("unknown.jpg"));

		assertThat(observation.provider()).isEqualTo("OPENAI");
		assertThat(observation.recognized()).isFalse();
		assertThat(observation.fallback()).isTrue();
	}

	@Test
	void recognizedWithoutSignalsBecomesFallbackObservation() {
		OpenAIImageObservationProvider provider = provider(request -> new OpenAIObservationResponse(
				true,
				BigDecimal.valueOf(0.7),
				null,
				null,
				null,
				null,
				null,
				null));

		ImageObservation observation = provider.observe(jpegRequest("empty.jpg"));

		assertThat(observation.fallback()).isTrue();
	}

	@Test
	void invalidConfidenceFallsBackToMock() {
		OpenAIImageObservationProvider provider = provider(request -> new OpenAIObservationResponse(
				true,
				BigDecimal.valueOf(1.2),
				List.of("DOG"),
				List.of(),
				null,
				List.of(),
				List.of(),
				List.of()));

		ImageObservation observation = provider.observe(jpegRequest("dog.jpg"));

		assertThat(observation.provider()).isEqualTo("LEGACY_MOCK");
		assertThat(observation.subjects()).containsExactly("DOG");
	}

	@Test
	void unsupportedMimeAndMissingBytesFallbackToMock() {
		OpenAIImageObservationProvider provider = provider(request -> {
			throw new AssertionError("OpenAI client should not be called");
		});

		ImageObservation heic = provider.observe(ImageObservationRequest.of(
				1L,
				"cat.HEIC",
				"image/heic",
				10,
				"image".getBytes()));
		ImageObservation noBytes = provider.observe(ImageObservationRequest.of(
				1L,
				"flower.jpg",
				"image/jpeg",
				10,
				null));

		assertThat(heic.provider()).isEqualTo("LEGACY_MOCK");
		assertThat(heic.subjects()).containsExactly("CAT");
		assertThat(noBytes.provider()).isEqualTo("LEGACY_MOCK");
		assertThat(noBytes.objects()).containsExactly("FLOWER");
	}

	@Test
	void imageLargerThanProviderLimitFallsBackToMock() {
		OpenAIObservationProperties properties = properties();
		properties.setMaxImageBytes(4);
		OpenAIImageObservationProvider provider = new OpenAIImageObservationProvider(properties, request -> {
			throw new AssertionError("OpenAI client should not be called");
		}, mock);

		ImageObservation observation = provider.observe(ImageObservationRequest.of(
				1L,
				"flower.jpg",
				"image/jpeg",
				10,
				"fake-image".getBytes()));

		assertThat(observation.provider()).isEqualTo("LEGACY_MOCK");
		assertThat(observation.objects()).containsExactly("FLOWER");
	}

	@Test
	void openAIClientFailureFallsBackToMockAndDoesNotExposeBytes() {
		OpenAIImageObservationProvider provider = provider(request -> {
			assertThat(request.toString()).doesNotContain("base64");
			throw new OpenAIObservationException("timeout");
		});

		ImageObservation observation = provider.observe(jpegRequest("flower.jpg"));

		assertThat(observation.provider()).isEqualTo("LEGACY_MOCK");
		assertThat(observation.objects()).containsExactly("FLOWER");
	}

	private OpenAIImageObservationProvider provider(OpenAIObservationClient client) {
		return new OpenAIImageObservationProvider(properties(), client, mock);
	}

	private OpenAIObservationProperties properties() {
		OpenAIObservationProperties properties = new OpenAIObservationProperties();
		properties.setProvider("openai");
		properties.getOpenai().setApiKey("test-key");
		properties.getOpenai().setModel("test-vision-model");
		return properties;
	}

	private ImageObservationRequest jpegRequest(String originalFileName) {
		byte[] imageBytes = "fake-image".getBytes();
		return ImageObservationRequest.of(
				1L,
				originalFileName,
				"image/jpeg",
				imageBytes.length,
				imageBytes);
	}
}
