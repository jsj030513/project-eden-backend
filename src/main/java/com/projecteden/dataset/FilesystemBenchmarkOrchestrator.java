package com.projecteden.dataset;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(prefix="eden.benchmark.orchestration",name="enabled",havingValue="true")
public class FilesystemBenchmarkOrchestrator implements BenchmarkOrchestrator {
	private final BenchmarkManager benchmarks; private final BenchmarkEvaluator evaluator; private final FilesystemQualityGateStore store; private final BenchmarkPredictionSourceResolver sources; private final BenchmarkQualityGate gate=new BenchmarkQualityGate();
	public FilesystemBenchmarkOrchestrator(BenchmarkManager benchmarks,BenchmarkEvaluator evaluator,FilesystemQualityGateStore store,BenchmarkPredictionSourceResolver sources){this.benchmarks=benchmarks;this.evaluator=evaluator;this.store=store;this.sources=sources;}
	public BenchmarkExecutionResult execute(BenchmarkExecutionRequest request){
		BenchmarkRun pending=benchmarks.createRun(request.datasetId(),request.revisionId(),request.modelIdentifier(),request.providerIdentifier());
		benchmarks.markRunning(request.datasetId(),pending.runId());
		try { java.util.Map<String,VisionGroundTruth> predictions=sources.resolve(request).predict(request.datasetId(),request.revisionId()); EvaluationResult result=evaluator.evaluateRevision(request.datasetId(),request.revisionId(),predictions); BenchmarkRun completed=benchmarks.finishRun(request.datasetId(),pending.runId(),result.metrics()); BenchmarkQualityGateResult quality=gate.evaluate(request.revisionId(),completed.runId(),result.metrics());store.persist(request.datasetId(),quality);return new BenchmarkExecutionResult(completed,result,quality); }
		catch(IllegalArgumentException exception){boolean unavailable="LOCAL_PROVIDER_DISABLED".equals(exception.getMessage()); boolean predictionMissing=exception.getMessage()!=null&&exception.getMessage().startsWith("PREDICTION_NOT_FOUND"); BenchmarkFailureCode code=unavailable?BenchmarkFailureCode.LOCAL_PROVIDER_DISABLED:(predictionMissing?BenchmarkFailureCode.PREDICTION_MISSING:BenchmarkFailureCode.EVALUATION_FAILED);BenchmarkRun failed=benchmarks.failRun(request.datasetId(),pending.runId(),new BenchmarkFailure(code,unavailable?"Local benchmark provider is not ready":"Evaluation could not be completed",java.time.Instant.now(),0,0));BenchmarkQualityDecision decision=unavailable?BenchmarkQualityDecision.PLATFORM_UNVERIFIED:BenchmarkQualityDecision.EVALUATION_FAILED;BenchmarkQualityGateResult quality=new BenchmarkQualityGateResult(decision,request.revisionId(),failed.runId(),0,"eden-benchmark-quality-gate-v1",java.util.List.of(code.name()),java.time.Instant.now());store.persist(request.datasetId(),quality);throw exception;}
	}
}
