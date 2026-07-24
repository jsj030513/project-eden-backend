package com.projecteden.dataset;
import java.time.Instant;
import java.util.List;
public record BenchmarkQualityGateResult(BenchmarkQualityDecision decision, String revisionId, String benchmarkRunId, int caseCount, String policyVersion, List<String> reasons, Instant evaluatedAt) { }
