package com.projecteden.memorytaxonomy.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecteden.memorytaxonomy.classification.MemoryClassificationResult;
import com.projecteden.memorytaxonomy.classification.MemoryClassificationService;
import com.projecteden.memorytaxonomy.observation.ImageObservation;
import com.projecteden.memorytaxonomy.observation.ImageObservationProvider;
import com.projecteden.memorytaxonomy.observation.ImageObservationProviderResolver;
import com.projecteden.memorytaxonomy.observation.ImageObservationRequest;
import com.projecteden.imagenormalization.ImageFormat;
import com.projecteden.imagenormalization.ImageNormalizationService;
import com.projecteden.imagenormalization.NormalizedImage;

class ImageEvaluationRunnerTests {

	@TempDir
	Path tempDir;

	@Test
	void evaluatesImagesThroughProviderAndClassificationWithoutPersisting() throws Exception {
		Path image = tempDir.resolve("dog.jpg");
		Files.write(image, "fake-image".getBytes());
		Path manifest = tempDir.resolve("manifest.json");
		Files.writeString(manifest, """
				[
				  {
				    "caseId":"dog-walk",
				    "imagePath":"%s",
				    "expectedPrimary":"ANIMAL",
				    "expectedSecondary":["WALK"],
				    "expectedTags":["DOG"]
				  }
				]
				""".formatted(image.toString().replace("\\", "\\\\")));
		Path output = tempDir.resolve("out");

		ImageObservationProvider provider = mock(ImageObservationProvider.class);
		ImageObservationProviderResolver resolver = mock(ImageObservationProviderResolver.class);
		MemoryClassificationService classificationService = mock(MemoryClassificationService.class);
		ImageNormalizationService normalizationService = mock(ImageNormalizationService.class);
		ImageObservation observation = ImageObservation.recognized(
				List.of("DOG"),
				List.of(),
				"PARK",
				List.of("WALKING"),
				List.of(),
				List.of(),
				"OPENAI",
				"test-model",
				BigDecimal.valueOf(0.9));
		when(resolver.resolve()).thenReturn(provider);
		when(provider.observe(any(ImageObservationRequest.class))).thenReturn(observation);
		when(classificationService.classify(observation)).thenReturn(new MemoryClassificationResult(
				"ANIMAL",
				List.of("WALK"),
				List.of("DOG"),
				null,
				false,
				BigDecimal.valueOf(0.9),
				Map.of()));
		when(normalizationService.normalize(any())).thenReturn(new NormalizedImage(
				"normalized".getBytes(), "image/jpeg", ImageFormat.JPEG, 1, 1, ImageFormat.JPEG, 1, 1,
				false, false, false, false, false, true, "checksum"));

		ImageEvaluationRunner runner = new ImageEvaluationRunner(
				new ImageEvaluationManifestReader(new ObjectMapper()),
				new ImageEvaluationReportWriter(new ImageEvaluationMetrics()),
				resolver,
				classificationService,
				normalizationService);

		ImageEvaluationSummary summary = runner.run(manifest, output, 10);

		assertThat(summary.totalCases()).isEqualTo(1);
		assertThat(summary.primaryAccuracy()).isEqualTo(1.0);
		assertThat(Files.readString(output.resolve("eden-vision-evaluation-v2.csv")))
				.contains("dog-walk")
				.doesNotContain(image.toString())
				.doesNotContain("fake-image");
		assertThat(Files.readString(output.resolve("eden-vision-evaluation-v2.md")))
				.contains("Total cases: 1")
				.doesNotContain(image.toString())
				.doesNotContain("fake-image");
		verify(provider).observe(any(ImageObservationRequest.class));
		verify(classificationService).classify(observation);
	}
}
