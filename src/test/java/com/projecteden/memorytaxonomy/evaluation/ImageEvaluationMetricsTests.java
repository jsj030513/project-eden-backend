package com.projecteden.memorytaxonomy.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class ImageEvaluationMetricsTests {

	private final ImageEvaluationMetrics metrics = new ImageEvaluationMetrics();

	@Test
	void summarizesAccuracyAndFallbackCounts() {
		ImageEvaluationSummary summary = metrics.summarize(List.of(
				result("case-1", "OPENAI", true, true, null, 10),
				result("case-2", "LEGACY_MOCK", false, false, "TIMEOUT", 20)));

		assertThat(summary.totalCases()).isEqualTo(2);
		assertThat(summary.providerSuccessCount()).isEqualTo(1);
		assertThat(summary.providerFailureCount()).isEqualTo(1);
		assertThat(summary.mockFallbackCount()).isEqualTo(1);
		assertThat(summary.primaryAccuracy()).isEqualTo(0.5);
		assertThat(summary.p95LatencyMs()).isEqualTo(20);
	}

	private ImageEvaluationResult result(
			String caseId,
			String provider,
			boolean primaryMatch,
			boolean recognized,
			String failureType,
			long latencyMs) {
		return new ImageEvaluationResult(
				caseId,
				"image/jpeg",
				100,
				provider,
				"model",
				recognized,
				"LEGACY_MOCK".equals(provider),
				primaryMatch ? "ANIMAL" : "NATURE",
				List.of(),
				List.of("CAT"),
				BigDecimal.valueOf(0.9),
				latencyMs,
				"ANIMAL",
				primaryMatch,
				List.of(),
				0,
				0,
				0,
				List.of("CAT"),
				1,
				0,
				0,
				failureType);
	}
}
