package com.projecteden.memorytaxonomy.observation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.village.domain.VillageCategory;

class LegacyRecognitionProjectionTests {

	private final LegacyRecognitionProjection projection = new LegacyRecognitionProjection();

	@Test
	void projectsObservationToLegacyRecognitionResult() {
		var result = projection.project(observation(List.of("CAT"), List.of(), null, List.of()));

		assertThat(result.recognizedObject()).isEqualTo(RecognizedObject.CAT);
		assertThat(result.category()).isEqualTo(VillageCategory.ANIMAL);
		assertThat(result.confidence()).isEqualTo(82);
		assertThat(result.recognized()).isTrue();
		assertThat(result.fallback()).isFalse();
	}

	@Test
	void projectsActivityAliasesToExistingLegacyObjects() {
		assertThat(projection.project(observation(List.of(), List.of(), null, List.of("STUDYING")))
				.recognizedObject()).isEqualTo(RecognizedObject.STUDY);
		assertThat(projection.project(observation(List.of(), List.of(), null, List.of("WORKING")))
				.recognizedObject()).isEqualTo(RecognizedObject.WORKSPACE);
	}

	@Test
	void projectsSupportedCocoAliasesWithoutAddingLegacyEnumValues() {
		assertThat(projection.project(observation(List.of(), List.of("SHEEP"), null, List.of())).recognizedObject())
				.isEqualTo(RecognizedObject.ANIMAL);
		assertThat(projection.project(observation(List.of(), List.of("TEDDY_BEAR"), null, List.of())).recognizedObject())
				.isEqualTo(RecognizedObject.OBJECT);
	}

	@Test
	void fallbackObservationProjectsToUnknown() {
		var result = projection.project(ImageObservation.fallback("MOCK", "mock-v1"));

		assertThat(result.recognizedObject()).isEqualTo(RecognizedObject.UNKNOWN);
		assertThat(result.category()).isEqualTo(VillageCategory.UNKNOWN);
		assertThat(result.confidence()).isZero();
		assertThat(result.recognized()).isFalse();
		assertThat(result.fallback()).isTrue();
	}

	@Test
	void noDetectionGeneralMemoryRemainsRecognizedWithoutInventingAnObject() {
		var result = projection.project(ImageObservation.generalMemory("LOCAL_YOLOX", "yolox-nano"));

		assertThat(result.recognizedObject()).isEqualTo(RecognizedObject.OBJECT);
		assertThat(result.recognized()).isTrue();
		assertThat(result.fallback()).isTrue();
	}

	private ImageObservation observation(
			List<String> subjects,
			List<String> objects,
			String scene,
			List<String> activities) {
		return ImageObservation.recognized(
				subjects,
				objects,
				scene,
				activities,
				List.of(),
				List.of(),
				"MOCK",
				"mock-v1",
				BigDecimal.valueOf(0.82));
	}
}
