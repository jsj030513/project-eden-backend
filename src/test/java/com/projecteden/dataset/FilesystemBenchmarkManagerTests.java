package com.projecteden.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class FilesystemBenchmarkManagerTests {

	@TempDir
	Path root;

	@Test
	void createsAndCompletesImmutableRunWithRevisionChecksumsAndReport() throws Exception {
		VisionDatasetId datasetId = new VisionDatasetId("eden-local");
		writeRevision(datasetId, "rev-000001", "dataset-checksum", "manifest-checksum");
		FilesystemBenchmarkManager benchmarks = new FilesystemBenchmarkManager(root);

		BenchmarkRun pending = benchmarks.createRun(datasetId, "rev-000001", "yolox-nano", "local");
		assertThat(pending.runId()).isEqualTo("run-000001");
		assertThat(pending.status()).isEqualTo(BenchmarkStatus.PENDING);
		assertThat(pending.datasetChecksum()).isEqualTo("dataset-checksum");
		assertThat(pending.manifestChecksum()).isEqualTo("manifest-checksum");
		assertThat(benchmarks.findRun(datasetId, pending.runId())).contains(pending);

		BenchmarkRun completed = benchmarks.finishRun(datasetId, pending.runId(), metrics());
		Path runDirectory = root.resolve("datasets/eden-local/benchmarks/run-000001");
		assertThat(completed.status()).isEqualTo(BenchmarkStatus.COMPLETED);
		assertThat(completed.finishedAt()).isNotNull();
		assertThat(Files.exists(runDirectory.resolve("benchmark.yml"))).isTrue();
		assertThat(Files.readString(runDirectory.resolve("report.md"))).contains("Revision: rev-000001").contains("Category accuracy: 0.87");
		assertThatThrownBy(() -> benchmarks.finishRun(datasetId, pending.runId(), metrics()))
				.hasMessageContaining("BENCHMARK_RUN_IMMUTABLE");

		BenchmarkRun next = benchmarks.createRun(datasetId, "rev-000001", "yolox-nano", "local");
		assertThat(benchmarks.listRuns(datasetId)).extracting(BenchmarkRun::runId).containsExactly("run-000001", "run-000002");
		assertThat(benchmarks.latestRun(datasetId)).contains(next);
	}

	@Test
	void rejectsUnknownRevisionAndInvalidMetrics() {
		FilesystemBenchmarkManager benchmarks = new FilesystemBenchmarkManager(root);
		VisionDatasetId datasetId = new VisionDatasetId("eden-local");
		assertThatThrownBy(() -> benchmarks.createRun(datasetId, "rev-000001", "model", "provider"))
				.hasMessageContaining("REVISION_NOT_FOUND");
		assertThatThrownBy(() -> new BenchmarkMetrics(BigDecimal.valueOf(1.1), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
				BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, 1))
				.hasMessageContaining("INVALID_BENCHMARK_METRIC");
	}

	private void writeRevision(VisionDatasetId datasetId, String revisionId, String datasetChecksum, String manifestChecksum) throws Exception {
		Path revision = root.resolve("datasets/eden-local/revisions").resolve(revisionId);
		Files.createDirectories(revision);
		yaml().writerWithDefaultPrettyPrinter().writeValue(revision.resolve("revision.yml").toFile(),
				new DatasetRevision(null, revisionId, datasetId, Instant.EPOCH, new RevisionMetadata("reviewer", "test"), 2,
						manifestChecksum, datasetChecksum, "summary-checksum", RevisionStatus.ACTIVE));
	}

	private BenchmarkMetrics metrics() {
		return new BenchmarkMetrics(new BigDecimal("0.87"), new BigDecimal("0.84"), new BigDecimal("0.83"), new BigDecimal("0.835"),
				new BigDecimal("0.82"), new BigDecimal("0.81"), new BigDecimal("0.80"), new BigDecimal("0.79"), new BigDecimal("0.78"),
				new BigDecimal("0.77"), new BigDecimal("0.05"), new BigDecimal("0.02"), 2);
	}

	private ObjectMapper yaml() {
		return new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());
	}
}
