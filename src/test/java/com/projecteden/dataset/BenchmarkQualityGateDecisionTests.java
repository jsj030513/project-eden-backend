package com.projecteden.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BenchmarkQualityGateDecisionTests {
	@Test void returnsPassFailAndInsufficientSampleDeterministically(){ BenchmarkQualityGate gate=new BenchmarkQualityGate(); assertThat(gate.evaluate("r","run",metrics(20, ".90")).decision()).isEqualTo(BenchmarkQualityDecision.PASS); assertThat(gate.evaluate("r","run",metrics(20, ".10")).decision()).isEqualTo(BenchmarkQualityDecision.FAIL); BenchmarkQualityGateResult insufficient=gate.evaluate("r","run",metrics(1,".90")); assertThat(insufficient.decision()).isEqualTo(BenchmarkQualityDecision.INSUFFICIENT_SAMPLE); assertThat(insufficient.policyVersion()).isEqualTo("eden-benchmark-quality-gate-v1"); assertThat(insufficient.reasons()).contains("MIN_CASE_COUNT"); }
	@Test void exposesTerminalFailureDecisions(){ assertThat(BenchmarkQualityDecision.valueOf("EVALUATION_FAILED")).isEqualTo(BenchmarkQualityDecision.EVALUATION_FAILED); assertThat(BenchmarkQualityDecision.valueOf("PLATFORM_UNVERIFIED")).isEqualTo(BenchmarkQualityDecision.PLATFORM_UNVERIFIED); }
	private BenchmarkMetrics metrics(int n,String v){ BigDecimal x=new BigDecimal(v);return new BenchmarkMetrics(x,x,x,x,x,x,x,x,x,x,BigDecimal.ZERO,BigDecimal.ZERO,n); }
}
