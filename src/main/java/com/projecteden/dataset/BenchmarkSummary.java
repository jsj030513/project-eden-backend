package com.projecteden.dataset;

public record BenchmarkSummary(
		String schemaVersion,
		String revisionId,
		BenchmarkStatus status,
		BenchmarkMetrics metrics) {
	public BenchmarkSummary {
		schemaVersion = schemaVersion == null ? "eden-benchmark-schema-v1" : schemaVersion;
	}
}
