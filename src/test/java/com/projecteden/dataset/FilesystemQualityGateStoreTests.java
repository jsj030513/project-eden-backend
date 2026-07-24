package com.projecteden.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class FilesystemQualityGateStoreTests {

	@TempDir
	Path root;

	@Test
	void persistsImmutableCompletedQualityGateAndFinalReport() throws Exception {
		FilesystemBenchmarkManager manager = managerWithRevision();
		VisionDatasetId datasetId = new VisionDatasetId("eden-local");
		BenchmarkRun run = manager.createRun(datasetId, "rev-000001", "model", "supplied");
		BenchmarkRun completed = manager.finishRun(datasetId, run.runId(), metrics(20));
		FilesystemQualityGateStore store = new FilesystemQualityGateStore(root.toString());
		BenchmarkQualityGateResult gate = new BenchmarkQualityGate().evaluate(completed.revisionId(), completed.runId(), completed.metrics());

		store.persist(datasetId, gate);

		Path directory = root.resolve("datasets/eden-local/benchmarks/").resolve(run.runId());
		BenchmarkQualityGateResult restored = yaml().readValue(directory.resolve("quality-gate.yml").toFile(), BenchmarkQualityGateResult.class);
		assertThat(restored).isEqualTo(gate);
		assertThat(Files.readString(directory.resolve("report.md")))
				.contains("## Quality Gate", "Decision: PASS", "Policy Version:", "### Evidence")
				.doesNotContain(root.toString(), "Exception", "at com.projecteden");
		assertThatThrownBy(() -> store.persist(datasetId, gate)).hasMessageContaining("QUALITY_GATE_IMMUTABLE");
	}

	@Test
	void persistsEvaluationFailedGateAndSafeFailureReport() throws Exception {
		FilesystemBenchmarkManager manager = managerWithRevision();
		VisionDatasetId datasetId = new VisionDatasetId("eden-local");
		BenchmarkRun pending = manager.createRun(datasetId, "rev-000001", "model", "supplied");
		manager.markRunning(datasetId, pending.runId());
		manager.failRun(datasetId, pending.runId(), new BenchmarkFailure(BenchmarkFailureCode.PREDICTION_MISSING,
				"A supplied prediction is missing", Instant.EPOCH, 1, 2));
		FilesystemQualityGateStore store = new FilesystemQualityGateStore(root.toString());
		BenchmarkQualityGateResult gate = new BenchmarkQualityGateResult(BenchmarkQualityDecision.EVALUATION_FAILED,
				"rev-000001", pending.runId(), 2, "eden-benchmark-quality-gate-v1", List.of("PREDICTION_MISSING"), Instant.EPOCH);

		store.persist(datasetId, gate);

		Path report = root.resolve("datasets/eden-local/benchmarks/").resolve(pending.runId()).resolve("report.md");
		assertThat(Files.readString(report)).contains("## Failure", "PREDICTION_MISSING", "Processed Cases: 1", "Total Cases: 2",
				"Decision: EVALUATION_FAILED").doesNotContain(root.toString(), "Exception", "at com.projecteden");
		assertThat(manager.findRun(datasetId, pending.runId()).orElseThrow().metrics()).isNull();
	}

	@Test
	void enforcesLifecycleTransitionsAndTerminalImmutability() throws Exception {
		FilesystemBenchmarkManager manager = managerWithRevision();
		VisionDatasetId datasetId = new VisionDatasetId("eden-local");
		BenchmarkRun pending = manager.createRun(datasetId, "rev-000001", "model", "supplied");
		assertThat(manager.markRunning(datasetId, pending.runId()).status()).isEqualTo(BenchmarkStatus.RUNNING);
		assertThatThrownBy(() -> manager.markRunning(datasetId, pending.runId())).hasMessageContaining("INVALID_BENCHMARK_TRANSITION");
		assertThat(manager.finishRun(datasetId, pending.runId(), metrics(1)).status()).isEqualTo(BenchmarkStatus.COMPLETED);
		assertThatThrownBy(() -> manager.failRun(datasetId, pending.runId(), failure())).hasMessageContaining("BENCHMARK_RUN_IMMUTABLE");

		BenchmarkRun retry = manager.createRun(datasetId, "rev-000001", "model", "supplied");
		manager.markRunning(datasetId, retry.runId());
		assertThat(manager.failRun(datasetId, retry.runId(), failure()).status()).isEqualTo(BenchmarkStatus.FAILED);
		assertThatThrownBy(() -> manager.finishRun(datasetId, retry.runId(), metrics(1))).hasMessageContaining("INVALID_BENCHMARK_TRANSITION");
		assertThat(retry.runId()).isNotEqualTo(pending.runId());
	}

	private FilesystemBenchmarkManager managerWithRevision() throws Exception {
		VisionDatasetId datasetId = new VisionDatasetId("eden-local");
		Path revision = root.resolve("datasets/eden-local/revisions/rev-000001");
		Files.createDirectories(revision);
		yaml().writeValue(revision.resolve("revision.yml").toFile(), new DatasetRevision(null, "rev-000001", datasetId,
				Instant.EPOCH, new RevisionMetadata(null, null), 1, "manifest", "dataset", "summary", RevisionStatus.ACTIVE));
		return new FilesystemBenchmarkManager(root);
	}

	private BenchmarkMetrics metrics(int cases) {
		return new BenchmarkMetrics(new BigDecimal("0.90"), new BigDecimal("0.90"), new BigDecimal("0.90"), new BigDecimal("0.90"),
				new BigDecimal("0.90"), new BigDecimal("0.90"), new BigDecimal("0.90"), new BigDecimal("0.90"), new BigDecimal("0.90"),
				new BigDecimal("0.90"), new BigDecimal("0.05"), new BigDecimal("0.05"), cases);
	}

	private BenchmarkFailure failure() {
		return new BenchmarkFailure(BenchmarkFailureCode.EVALUATION_FAILED, "Evaluation could not be completed", Instant.EPOCH, 0, 1);
	}

	private ObjectMapper yaml() {
		return new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());
	}
}
