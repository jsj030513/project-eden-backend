package com.projecteden.memorytaxonomy.observation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.projecteden.photo.domain.Photo;

class MockImageObservationProviderTests {

	private final MockImageObservationProvider provider = new MockImageObservationProvider();

	@Test
	void observesCatDogFlowerFoodWaterStudyWorkAndUnknown() {
		assertThat(provider.observe(photo("cat.jpg")).subjects()).containsExactly("CAT");
		assertThat(provider.observe(photo("dog.jpg")).subjects()).containsExactly("DOG");
		assertThat(provider.observe(photo("flower.jpg")).objects()).containsExactly("FLOWER");
		assertThat(provider.observe(photo("bread.jpg")).objects()).containsExactly("BREAD");
		assertThat(provider.observe(photo("river.jpg")).objects()).containsExactly("RIVER");
		assertThat(provider.observe(photo("study-book.jpg")).activities()).containsExactly("STUDYING");
		assertThat(provider.observe(photo("coding-laptop.jpg")).activities()).containsExactly("CODING");
		assertThat(provider.observe(photo("IMG_0001.HEIC")).fallback()).isTrue();
	}

	@Test
	void keepsLegacyKeywordPriority() {
		ImageObservation parkPath = provider.observe(photo("park-path.jpg"));
		ImageObservation notebookComputer = provider.observe(photo("notebook-computer.jpg"));

		assertThat(parkPath.objects()).containsExactly("PATH");
		assertThat(notebookComputer.objects()).containsExactly("COMPUTER");
	}

	@Test
	void exposesProviderMetadata() {
		ImageObservation observation = provider.observe(photo("flower.jpg"));

		assertThat(provider.provider()).isEqualTo("LEGACY_MOCK");
		assertThat(provider.modelVersion()).isEqualTo("mock-filename-v1");
		assertThat(observation.provider()).isEqualTo("LEGACY_MOCK");
		assertThat(observation.modelVersion()).isEqualTo("mock-filename-v1");
		assertThat(observation.confidence()).isEqualByComparingTo("0.95");
	}

	private Photo photo(String originalFileName) {
		return Photo.create(
				null,
				null,
				originalFileName,
				originalFileName,
				"image/jpeg",
				1,
				"/uploads/photos/" + originalFileName);
	}
}
