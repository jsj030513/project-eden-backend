package com.projecteden.memorytaxonomy.evaluation;

import java.util.List;

public record ImageEvaluationCase(
		String caseId,
		String imagePath,
		String expectedPrimary,
		List<String> expectedSecondary,
		List<String> expectedTags,
		Boolean expectedRecognized,
		String notes,
		Integer manifestVersion,
		Boolean enabled,
		List<String> expectedObjects,
		List<String> expectedActivities,
		List<String> expectedRelationships,
		Boolean expectedFallback) {
	public ImageEvaluationCase(String caseId, String imagePath, String expectedPrimary, List<String> expectedSecondary, List<String> expectedTags, Boolean expectedRecognized, String notes) {
		this(caseId, imagePath, expectedPrimary, expectedSecondary, expectedTags, expectedRecognized, notes, 1, true, List.of(), List.of(), List.of(), null);
	}

	public List<String> expectedSecondary() {
		return expectedSecondary == null ? List.of() : expectedSecondary;
	}

	public List<String> expectedTags() {
		return expectedTags == null ? List.of() : expectedTags;
	}
	public List<String> expectedObjects() { return expectedObjects == null ? List.of() : List.copyOf(new java.util.LinkedHashSet<>(expectedObjects)); }
	public List<String> expectedActivities() { return expectedActivities == null ? List.of() : List.copyOf(new java.util.LinkedHashSet<>(expectedActivities)); }
	public List<String> expectedRelationships() { return expectedRelationships == null ? List.of() : List.copyOf(new java.util.LinkedHashSet<>(expectedRelationships)); }
	public boolean isEnabled() { return enabled == null || enabled; }
}
