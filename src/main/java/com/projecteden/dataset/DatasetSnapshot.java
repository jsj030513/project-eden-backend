package com.projecteden.dataset;

import java.util.Map;

public record DatasetSnapshot(
		int caseCount,
		int approvedCount,
		int correctedCount,
		int rejectedCount,
		int pendingCount,
		Map<String, Integer> categoryCount,
		Map<String, Integer> tagCount) {

	public DatasetSnapshot {
		categoryCount = categoryCount == null ? Map.of() : Map.copyOf(categoryCount);
		tagCount = tagCount == null ? Map.of() : Map.copyOf(tagCount);
	}
}
