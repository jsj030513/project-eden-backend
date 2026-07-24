package com.projecteden.memorytaxonomy.evaluation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class ImageEvaluationMetrics {

	public ImageEvaluationSummary summarize(List<ImageEvaluationResult> results) {
		int total = results.size();
		int scoredPrimary = (int) results.stream()
				.filter(result -> result.primaryMatch() != null)
				.count();
		int primaryMatches = (int) results.stream()
				.filter(result -> Boolean.TRUE.equals(result.primaryMatch()))
				.count();
		int secondaryTp = results.stream().mapToInt(ImageEvaluationResult::secondaryTruePositive).sum();
		int secondaryFp = results.stream().mapToInt(ImageEvaluationResult::secondaryFalsePositive).sum();
		int secondaryFn = results.stream().mapToInt(ImageEvaluationResult::secondaryFalseNegative).sum();
		int tagTp = results.stream().mapToInt(ImageEvaluationResult::tagTruePositive).sum();
		int tagFp = results.stream().mapToInt(ImageEvaluationResult::tagFalsePositive).sum();
		int tagFn = results.stream().mapToInt(ImageEvaluationResult::tagFalseNegative).sum();
		List<Long> latencies = results.stream()
				.map(ImageEvaluationResult::latencyMs)
				.sorted(Comparator.naturalOrder())
				.toList();

		return new ImageEvaluationSummary(
				total,
				scoredPrimary,
				(int) results.stream().filter(result -> !result.fallback()).count(),
				(int) results.stream().filter(result -> result.failureType() != null).count(),
				(int) results.stream().filter(result -> "LEGACY_MOCK".equals(result.provider())).count(),
				(int) results.stream().filter(result -> !result.recognized()).count(),
				ratio(primaryMatches, scoredPrimary),
				ratio(secondaryTp, secondaryTp + secondaryFp),
				ratio(secondaryTp, secondaryTp + secondaryFn),
				ratio(tagTp, tagTp + tagFp),
				ratio(tagTp, tagTp + tagFn),
				results.stream().mapToLong(ImageEvaluationResult::latencyMs).average().orElse(0),
				percentile(latencies, 0.50),
				percentile(latencies, 0.95),
				groupBy(results, ImageEvaluationResult::mimeType),
				groupBy(results, ImageEvaluationResult::failureType));
	}

	private double ratio(int numerator, int denominator) {
		if (denominator == 0) {
			return 0;
		}
		return (double) numerator / denominator;
	}

	private long percentile(List<Long> sortedValues, double percentile) {
		if (sortedValues.isEmpty()) {
			return 0;
		}
		int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
		return sortedValues.get(Math.max(0, Math.min(index, sortedValues.size() - 1)));
	}

	private Map<String, Long> groupBy(
			List<ImageEvaluationResult> results,
			java.util.function.Function<ImageEvaluationResult, String> classifier) {
		return results.stream()
				.collect(Collectors.groupingBy(
						result -> classifier.apply(result) == null ? "NONE" : classifier.apply(result),
						Collectors.counting()));
	}
}
