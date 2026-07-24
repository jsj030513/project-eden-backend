package com.projecteden.memorytaxonomy.classification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record MemoryClassificationResult(
		String primaryCategory,
		List<String> secondaryCategories,
		List<String> tags,
		String summary,
		boolean fallback,
		BigDecimal confidence,
		Map<String, Object> observation) {
}
