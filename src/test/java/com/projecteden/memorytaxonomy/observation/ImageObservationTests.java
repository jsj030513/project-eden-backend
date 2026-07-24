package com.projecteden.memorytaxonomy.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class ImageObservationTests {

	@Test
	void collectionsAreImmutableAndDuplicatesAreRemoved() {
		ImageObservation observation = ImageObservation.recognized(
				List.of("CAT", "CAT", "DOG"),
				List.of("FLOWER", "FLOWER"),
				"PARK",
				List.of("WALKING", "WALKING"),
				List.of("FRIEND", "FRIEND"),
				List.of("WARM", "WARM"),
				"MOCK",
				"mock-v1",
				BigDecimal.valueOf(0.82));

		assertThat(observation.subjects()).containsExactly("CAT", "DOG");
		assertThat(observation.objects()).containsExactly("FLOWER");
		assertThat(observation.activities()).containsExactly("WALKING");
		assertThat(observation.relationships()).containsExactly("FRIEND");
		assertThat(observation.moodSignals()).containsExactly("WARM");
		assertThatThrownBy(() -> observation.subjects().add("BIRD"))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void confidenceAndFallbackArePreserved() {
		ImageObservation recognized = ImageObservation.recognized(
				List.of("CAT"),
				List.of(),
				null,
				List.of(),
				List.of(),
				List.of(),
				"MOCK",
				"mock-v1",
				BigDecimal.valueOf(0.82));
		ImageObservation fallback = ImageObservation.fallback("MOCK", "mock-v1");

		assertThat(recognized.confidence()).isEqualByComparingTo("0.82");
		assertThat(recognized.recognized()).isTrue();
		assertThat(recognized.fallback()).isFalse();
		assertThat(fallback.confidence()).isEqualByComparingTo("0");
		assertThat(fallback.recognized()).isFalse();
		assertThat(fallback.fallback()).isTrue();
	}
}
