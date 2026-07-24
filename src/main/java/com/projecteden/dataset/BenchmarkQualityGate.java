package com.projecteden.dataset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
public final class BenchmarkQualityGate {
	public BenchmarkQualityGateResult evaluate(String revisionId, String runId, BenchmarkMetrics m) {
		if (m.caseCount() < 20) return result(BenchmarkQualityDecision.INSUFFICIENT_SAMPLE, revisionId, runId, m.caseCount(), List.of("MIN_CASE_COUNT"));
		List<String> failed = new ArrayList<>();
		if (m.categoryAccuracy().compareTo(java.math.BigDecimal.valueOf(.80)) < 0) failed.add("CATEGORY_ACCURACY");
		if (m.tagPrecision().compareTo(java.math.BigDecimal.valueOf(.80)) < 0) failed.add("TAG_PRECISION");
		if (m.tagRecall().compareTo(java.math.BigDecimal.valueOf(.70)) < 0) failed.add("TAG_RECALL");
		if (m.objectPrecision().compareTo(java.math.BigDecimal.valueOf(.85)) < 0) failed.add("OBJECT_PRECISION");
		if (m.objectRecall().compareTo(java.math.BigDecimal.valueOf(.70)) < 0) failed.add("OBJECT_RECALL");
		if (m.fallbackRate().compareTo(java.math.BigDecimal.valueOf(.20)) > 0) failed.add("FALLBACK_RATE");
		if (m.unknownRate().compareTo(java.math.BigDecimal.valueOf(.20)) > 0) failed.add("UNKNOWN_RATE");
		return result(failed.isEmpty() ? BenchmarkQualityDecision.PASS : BenchmarkQualityDecision.FAIL, revisionId, runId, m.caseCount(), failed);
	}
	private BenchmarkQualityGateResult result(BenchmarkQualityDecision d,String r,String run,int cases,List<String> reasons){return new BenchmarkQualityGateResult(d,r,run,cases,"eden-benchmark-quality-gate-v1",List.copyOf(reasons),Instant.now());}
}
