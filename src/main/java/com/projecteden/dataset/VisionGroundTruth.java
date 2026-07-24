package com.projecteden.dataset;

import java.util.List;

public record VisionGroundTruth(List<String> objects, List<String> activities, List<String> relationships, String category, List<String> secondaryCategories, List<String> tags, boolean fallback) {
	public VisionGroundTruth { objects = stable(objects); activities = stable(activities); relationships = stable(relationships); secondaryCategories = stable(secondaryCategories); tags = stable(tags); if (fallback && (!objects.isEmpty() || !activities.isEmpty() || !relationships.isEmpty() || category != null || !secondaryCategories.isEmpty() || !tags.isEmpty())) throw new IllegalArgumentException("Fallback ground truth cannot contain signals"); }
	public VisionGroundTruth(List<String> objects, List<String> activities, List<String> relationships, String category, List<String> tags, boolean fallback) { this(objects, activities, relationships, category, List.of(), tags, fallback); }
	private static List<String> stable(List<String> values) { return values == null ? List.of() : values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).map(String::toUpperCase).distinct().sorted().toList(); }
}
