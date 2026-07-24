package com.projecteden.memorytaxonomy.evaluation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.springframework.stereotype.Component;

@Component
public class ImageEvaluationReportWriter {

	private static final String CSV_FILE_NAME = "eden-vision-evaluation-v2.csv";
	private static final String SUMMARY_FILE_NAME = "eden-vision-evaluation-v2.md";

	private final ImageEvaluationMetrics metrics;

	public ImageEvaluationReportWriter(ImageEvaluationMetrics metrics) {
		this.metrics = metrics;
	}

	public ImageEvaluationSummary write(Path outputDirectory, List<ImageEvaluationResult> results) {
		try {
			Files.createDirectories(outputDirectory);
			ImageEvaluationSummary summary = metrics.summarize(results);
			Files.writeString(
					outputDirectory.resolve(CSV_FILE_NAME),
					csv(results),
					StandardCharsets.UTF_8);
			Files.writeString(
					outputDirectory.resolve(SUMMARY_FILE_NAME),
					markdown(summary),
					StandardCharsets.UTF_8);
			return summary;
		} catch (IOException ex) {
			throw new IllegalArgumentException("이미지 평가 리포트를 저장할 수 없습니다.", ex);
		}
	}

	private String csv(List<ImageEvaluationResult> results) {
		StringBuilder builder = new StringBuilder();
		builder.append(String.join(",",
				"manifestVersion","caseId","status",
				"mimeType",
				"fileSize",
				"provider",
				"modelVersion",
				"recognized",
				"fallback",
				"primaryCategory",
				"secondaryCategories",
				"tags",
				"confidence",
				"latencyMs",
				"expectedPrimary",
				"primaryMatch",
				"secondaryTruePositive",
				"secondaryFalsePositive",
				"secondaryFalseNegative",
				"tagTruePositive",
				"tagFalsePositive",
				"tagFalseNegative",
				"failureType","expectedObjects","predictedObjects","expectedActivities","predictedActivities","expectedRelationships","predictedRelationships","expectedCategory","predictedCategory","expectedFallback","predictedFallback","objectCorrect","activityCorrect","relationshipCorrect","taxonomyCorrect","fallbackCorrect","errorCode"));
		builder.append('\n');
		for (ImageEvaluationResult result : results) {
			builder.append(String.join(",",
					"1", escape(result.caseId()), result.failureType() == null ? "PASS" : "ERROR",
					escape(result.mimeType()),
					String.valueOf(result.fileSize()),
					escape(result.provider()),
					escape(result.modelVersion()),
					String.valueOf(result.recognized()),
					String.valueOf(result.fallback()),
					escape(result.primaryCategory()),
					escape(String.join("|", result.secondaryCategories())),
					escape(String.join("|", result.tags())),
					result.confidence() == null ? "" : result.confidence().toPlainString(),
					String.valueOf(result.latencyMs()),
					escape(result.expectedPrimary()),
					result.primaryMatch() == null ? "" : result.primaryMatch().toString(),
					String.valueOf(result.secondaryTruePositive()),
					String.valueOf(result.secondaryFalsePositive()),
					String.valueOf(result.secondaryFalseNegative()),
					String.valueOf(result.tagTruePositive()),
					String.valueOf(result.tagFalsePositive()),
					String.valueOf(result.tagFalseNegative()),
					escape(result.failureType()), "", "", "", "", "", "", escape(result.expectedPrimary()), escape(result.primaryCategory()), "", String.valueOf(result.fallback()), "", "", "", result.primaryMatch() == null ? "" : result.primaryMatch().toString(), "", escape(result.failureType())));
			builder.append('\n');
		}
		return builder.toString();
	}

	private String markdown(ImageEvaluationSummary summary) {
		return """
				# Eden Vision Evaluation Report

				## Counts

				- Total cases: %d
				- Scored primary cases: %d
				- Provider success: %d
				- Provider failure: %d
				- Mock fallback: %d
				- UNKNOWN: %d

				## Accuracy

				- Primary accuracy: %.4f
				- Secondary precision: %.4f
				- Secondary recall: %.4f
				- Tag precision: %.4f
				- Tag recall: %.4f

				## Latency

				- Average latency ms: %.2f
				- P50 latency ms: %d
				- P95 latency ms: %d

				## MIME Breakdown

				%s

				## Failure Breakdown

				%s
				""".formatted(
				summary.totalCases(),
				summary.scoredPrimaryCases(),
				summary.providerSuccessCount(),
				summary.providerFailureCount(),
				summary.mockFallbackCount(),
				summary.unknownCount(),
				summary.primaryAccuracy(),
				summary.secondaryPrecision(),
				summary.secondaryRecall(),
				summary.tagPrecision(),
				summary.tagRecall(),
				summary.averageLatencyMs(),
				summary.p50LatencyMs(),
				summary.p95LatencyMs(),
				bullets(summary.mimeBreakdown()),
				bullets(summary.failureBreakdown()));
	}

	private String bullets(Map<String, Long> values) {
		if (values.isEmpty()) {
			return "- NONE: 0";
		}
		StringJoiner joiner = new StringJoiner("\n");
		values.forEach((key, value) -> joiner.add("- " + key + ": " + value));
		return joiner.toString();
	}

	private String escape(String value) {
		if (value == null) {
			return "";
		}
		String escaped = value.replace("\"", "\"\"");
		if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
			return "\"" + escaped + "\"";
		}
		return escaped;
	}
}
