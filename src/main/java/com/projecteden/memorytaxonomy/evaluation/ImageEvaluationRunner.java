package com.projecteden.memorytaxonomy.evaluation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.projecteden.memorytaxonomy.classification.MemoryClassificationResult;
import com.projecteden.memorytaxonomy.classification.MemoryClassificationService;
import com.projecteden.memorytaxonomy.observation.ImageObservation;
import com.projecteden.memorytaxonomy.observation.ImageObservationProvider;
import com.projecteden.memorytaxonomy.observation.ImageObservationProviderResolver;
import com.projecteden.memorytaxonomy.observation.ImageObservationRequest;
import com.projecteden.memorytaxonomy.observation.UploadedImagePayload;
import com.projecteden.imagenormalization.ImageNormalizationService;

@Component
public class ImageEvaluationRunner {

	private final ImageEvaluationManifestReader manifestReader;
	private final ImageEvaluationReportWriter reportWriter;
	private final ImageObservationProviderResolver providerResolver;
	private final MemoryClassificationService classificationService;
	private final ImageNormalizationService imageNormalizationService;

	public ImageEvaluationRunner(
			ImageEvaluationManifestReader manifestReader,
			ImageEvaluationReportWriter reportWriter,
			ImageObservationProviderResolver providerResolver,
			MemoryClassificationService classificationService,
			ImageNormalizationService imageNormalizationService) {
		this.manifestReader = manifestReader;
		this.reportWriter = reportWriter;
		this.providerResolver = providerResolver;
		this.classificationService = classificationService;
		this.imageNormalizationService = imageNormalizationService;
	}

	public ImageEvaluationSummary run(Path manifestPath, Path outputDirectory, int maxCases) {
		List<ImageEvaluationCase> cases = manifestReader.read(manifestPath, maxCases);
		List<ImageEvaluationResult> results = cases.stream()
				.map(this::evaluate)
				.toList();
		return reportWriter.write(outputDirectory, results);
	}

	private ImageEvaluationResult evaluate(ImageEvaluationCase evaluationCase) {
		long startedAt = System.nanoTime();
		try {
			Path imagePath = Path.of(evaluationCase.imagePath());
			if (!Files.exists(imagePath)) {
				return failure(evaluationCase, "FILE_NOT_FOUND", latencyMs(startedAt));
			}
			byte[] bytes = Files.readAllBytes(imagePath);
			String mimeType = mimeType(imagePath);
			ImageObservationProvider provider = providerResolver.resolve();
			var normalized = imageNormalizationService.normalize(UploadedImagePayload.of(
					imagePath.getFileName().toString(), mimeType, bytes.length, bytes));
			ImageObservation observation = provider.observe(ImageObservationRequest.normalized(null, null, normalized));
			MemoryClassificationResult classification = classificationService.classify(observation);
			return result(evaluationCase, mimeType, bytes.length, observation, classification, latencyMs(startedAt));
		} catch (IOException ex) {
			return failure(evaluationCase, "IMAGE_READ_FAILED", latencyMs(startedAt));
		} catch (RuntimeException ex) {
			return failure(evaluationCase, ex.getClass().getSimpleName(), latencyMs(startedAt));
		}
	}

	private ImageEvaluationResult result(
			ImageEvaluationCase evaluationCase,
			String mimeType,
			long fileSize,
			ImageObservation observation,
			MemoryClassificationResult classification,
			long latencyMs) {
		List<String> secondary = safeList(classification.secondaryCategories());
		List<String> tags = safeList(classification.tags());
		List<String> expectedSecondary = normalize(evaluationCase.expectedSecondary());
		List<String> expectedTags = normalize(evaluationCase.expectedTags());
		String expectedPrimary = normalize(evaluationCase.expectedPrimary());
		String primary = normalize(classification.primaryCategory());

		return new ImageEvaluationResult(
				evaluationCase.caseId(),
				mimeType,
				fileSize,
				observation.provider(),
				observation.modelVersion(),
				observation.recognized(),
				observation.fallback(),
				primary,
				secondary,
				tags,
				observation.confidence(),
				latencyMs,
				expectedPrimary,
				expectedPrimary == null ? null : expectedPrimary.equals(primary),
				expectedSecondary,
				truePositive(secondary, expectedSecondary),
				falsePositive(secondary, expectedSecondary),
				falseNegative(secondary, expectedSecondary),
				expectedTags,
				truePositive(tags, expectedTags),
				falsePositive(tags, expectedTags),
				falseNegative(tags, expectedTags),
				null);
	}

	private ImageEvaluationResult failure(
			ImageEvaluationCase evaluationCase,
			String failureType,
			long latencyMs) {
		return new ImageEvaluationResult(
				evaluationCase.caseId(),
				null,
				0,
				null,
				null,
				false,
				true,
				null,
				List.of(),
				List.of(),
				null,
				latencyMs,
				normalize(evaluationCase.expectedPrimary()),
				null,
				normalize(evaluationCase.expectedSecondary()),
				0,
				0,
				normalize(evaluationCase.expectedSecondary()).size(),
				normalize(evaluationCase.expectedTags()),
				0,
				0,
				normalize(evaluationCase.expectedTags()).size(),
				failureType);
	}

	private String mimeType(Path imagePath) throws IOException {
		String probed = Files.probeContentType(imagePath);
		if (probed != null && !probed.isBlank()) {
			return probed;
		}
		String name = imagePath.getFileName().toString().toLowerCase(Locale.ROOT);
		if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
			return "image/jpeg";
		}
		if (name.endsWith(".png")) {
			return "image/png";
		}
		if (name.endsWith(".webp")) {
			return "image/webp";
		}
		if (name.endsWith(".gif")) {
			return "image/gif";
		}
		if (name.endsWith(".heic")) {
			return "image/heic";
		}
		if (name.endsWith(".heif")) {
			return "image/heif";
		}
		return "application/octet-stream";
	}

	private List<String> safeList(List<String> values) {
		return values == null ? List.of() : values;
	}

	private List<String> normalize(List<String> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		LinkedHashSet<String> normalized = new LinkedHashSet<>();
		for (String value : values) {
			String code = normalize(value);
			if (code != null) {
				normalized.add(code);
			}
		}
		return List.copyOf(normalized);
	}

	private String normalize(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim().toUpperCase(Locale.ROOT);
	}

	private int truePositive(List<String> actual, List<String> expected) {
		Set<String> expectedSet = Set.copyOf(expected);
		return (int) actual.stream()
				.filter(expectedSet::contains)
				.count();
	}

	private int falsePositive(List<String> actual, List<String> expected) {
		Set<String> expectedSet = Set.copyOf(expected);
		return (int) actual.stream()
				.filter(value -> !expectedSet.contains(value))
				.count();
	}

	private int falseNegative(List<String> actual, List<String> expected) {
		Set<String> actualSet = Set.copyOf(actual);
		return (int) expected.stream()
				.filter(value -> !actualSet.contains(value))
				.count();
	}

	private long latencyMs(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000;
	}
}
