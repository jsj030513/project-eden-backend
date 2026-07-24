package com.projecteden.memorytaxonomy.evaluation;

import java.math.BigDecimal;
import java.util.List;

public record ImageEvaluationResult(
		String caseId,
		String mimeType,
		long fileSize,
		String provider,
		String modelVersion,
		boolean recognized,
		boolean fallback,
		String primaryCategory,
		List<String> secondaryCategories,
		List<String> tags,
		BigDecimal confidence,
		long latencyMs,
		String expectedPrimary,
		Boolean primaryMatch,
		List<String> expectedSecondary,
		int secondaryTruePositive,
		int secondaryFalsePositive,
		int secondaryFalseNegative,
		List<String> expectedTags,
		int tagTruePositive,
		int tagFalsePositive,
		int tagFalseNegative,
		String failureType) {
}
