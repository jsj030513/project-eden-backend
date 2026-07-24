package com.projecteden.dataset;

import java.time.Instant;

public record BenchmarkRun(
		String schemaVersion,
		String runId,
		VisionDatasetId datasetId,
		String revisionId,
		Instant startedAt,
		Instant finishedAt,
		BenchmarkStatus status,
		String model,
		String provider,
		String datasetChecksum,
		String manifestChecksum,
		BenchmarkMetrics metrics,
		BenchmarkFailure failure) {

	public BenchmarkRun {
		schemaVersion = schemaVersion == null ? "eden-benchmark-schema-v1" : schemaVersion;
		if (runId == null || !runId.matches("run-[0-9]{6}")) throw new IllegalArgumentException("INVALID_BENCHMARK_RUN_ID");
		if (datasetId == null || revisionId == null || startedAt == null || status == null || model == null || model.isBlank()
				|| provider == null || provider.isBlank() || datasetChecksum == null || manifestChecksum == null) {
			throw new IllegalArgumentException("INVALID_BENCHMARK_RUN");
		}
		if (status == BenchmarkStatus.COMPLETED && (finishedAt == null || metrics == null)) throw new IllegalArgumentException("INCOMPLETE_BENCHMARK_RUN");
	}
	public BenchmarkRun(String s,String r,VisionDatasetId d,String v,Instant a,Instant b,BenchmarkStatus st,String m,String p,String dc,String mc,BenchmarkMetrics metrics){this(s,r,d,v,a,b,st,m,p,dc,mc,metrics,null);}
}
