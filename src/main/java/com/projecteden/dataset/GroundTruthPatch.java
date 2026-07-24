package com.projecteden.dataset;

import java.util.List;

/** A null field is unchanged; a supplied collection replaces only that collection. */
public record GroundTruthPatch(
		String category,
		List<String> secondaryCategories,
		List<String> tags,
		List<String> objects,
		List<String> activities,
		List<String> relationships,
		Boolean fallback,
		String notes) {
}
