package com.projecteden.dataset;

import java.nio.file.Path;

public final class DatasetPathResolver {
	private final Path root;
	public DatasetPathResolver(Path root) { if (root == null || root.toString().isBlank()) throw new IllegalArgumentException("Dataset root is required"); this.root = root.toAbsolutePath().normalize(); }
	public Path dataset(VisionDatasetId id) { return safe(Path.of("datasets", id.value())); }
	public Path caseDirectory(VisionDatasetId datasetId, VisionDatasetCaseId caseId) { return safe(Path.of("datasets", datasetId.value(), "cases", caseId.value())); }
	public Path reviewDirectory(VisionDatasetId datasetId) { return safe(Path.of("datasets", datasetId.value(), "reviews")); }
	public Path reviewFile(VisionDatasetId datasetId, String reviewId) { return safe(Path.of("datasets", datasetId.value(), "reviews", reviewId + ".yml")); }
	public Path revisionDirectory(VisionDatasetId datasetId) { return safe(Path.of("datasets", datasetId.value(), "revisions")); }
	public Path revisionDirectory(VisionDatasetId datasetId, String revisionId) { return safe(Path.of("datasets", datasetId.value(), "revisions", revisionId)); }
	public Path benchmarkDirectory(VisionDatasetId datasetId) { return safe(Path.of("datasets", datasetId.value(), "benchmarks")); }
	public Path benchmarkDirectory(VisionDatasetId datasetId, String runId) { return safe(Path.of("datasets", datasetId.value(), "benchmarks", runId)); }
	public Path collectionDirectory(VisionDatasetId datasetId) { return safe(Path.of("datasets", datasetId.value(), "collection")); }
	public Path collectionPlanFile(VisionDatasetId datasetId, String planId) { return safe(Path.of("datasets", datasetId.value(), "collection", "plans", planId + ".yml")); }
	public Path collectionCaseFile(VisionDatasetId datasetId, VisionDatasetCaseId caseId) { return safe(Path.of("datasets", datasetId.value(), "collection", "cases", caseId.value() + ".yml")); }
	public Path collectionCoverageFile(VisionDatasetId datasetId, String planId) { return safe(Path.of("datasets", datasetId.value(), "collection", "reports", planId + "-coverage.yml")); }
	public Path safe(Path relative) { if (relative.isAbsolute()) throw new IllegalArgumentException("Absolute child path is not allowed"); Path resolved = root.resolve(relative).normalize(); if (!resolved.startsWith(root)) throw new IllegalArgumentException("Path escape detected"); Path cursor=root; for(Path part:root.relativize(resolved)){cursor=cursor.resolve(part); if(java.nio.file.Files.isSymbolicLink(cursor)) throw new IllegalArgumentException("Symlink paths are not allowed");} return resolved; }
}
