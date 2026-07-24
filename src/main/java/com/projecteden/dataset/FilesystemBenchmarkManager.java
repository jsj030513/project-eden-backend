package com.projecteden.dataset;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Component
@ConditionalOnProperty(prefix = "eden.dataset", name = "enabled", havingValue = "true")
public class FilesystemBenchmarkManager implements BenchmarkManager {

	private final DatasetPathResolver paths;
	private final ObjectMapper yaml;

	public FilesystemBenchmarkManager(@Value("${eden.dataset.root:}") String configuredRoot, ObjectMapper ignored) {
		this(configuredRoot == null || configuredRoot.isBlank()
				? Path.of(System.getenv().getOrDefault("EDEN_DATASET_ROOT", System.getProperty("user.home") + "/.project-eden/datasets"))
				: Path.of(configuredRoot));
	}

	FilesystemBenchmarkManager(Path root) {
		this.paths = new DatasetPathResolver(root);
		this.yaml = new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule())
				.setSerializationInclusion(JsonInclude.Include.NON_NULL)
				.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
	}

	@Override
	public BenchmarkRun createRun(VisionDatasetId datasetId, String revisionId, String model, String provider) {
		DatasetRevision revision = read(paths.revisionDirectory(datasetId, revisionId).resolve("revision.yml"), DatasetRevision.class)
				.orElseThrow(() -> new IllegalArgumentException("REVISION_NOT_FOUND"));
		String runId = String.format("run-%06d", listRuns(datasetId).size() + 1);
		BenchmarkRun run = new BenchmarkRun(null, runId, datasetId, revisionId, Instant.now(), null, BenchmarkStatus.PENDING,
				model, provider, revision.datasetChecksum(), revision.manifestChecksum(), null);
		writeRun(run);
		return run;
	}

	@Override
	public BenchmarkRun finishRun(VisionDatasetId datasetId, String runId, BenchmarkMetrics metrics) {
		BenchmarkRun current = findRun(datasetId, runId).orElseThrow(() -> new IllegalArgumentException("BENCHMARK_RUN_NOT_FOUND"));
		if (current.status() == BenchmarkStatus.COMPLETED) throw new IllegalArgumentException("BENCHMARK_RUN_IMMUTABLE");
		if (current.status() == BenchmarkStatus.FAILED) throw new IllegalArgumentException("INVALID_BENCHMARK_TRANSITION");
		BenchmarkRun completed = new BenchmarkRun(current.schemaVersion(), current.runId(), current.datasetId(), current.revisionId(),
				current.startedAt(), Instant.now(), BenchmarkStatus.COMPLETED, current.model(), current.provider(),
				current.datasetChecksum(), current.manifestChecksum(), metrics);
		writeRun(completed);
		return completed;
	}

	@Override public BenchmarkRun markRunning(VisionDatasetId datasetId,String runId){BenchmarkRun current=findRun(datasetId,runId).orElseThrow(()->new IllegalArgumentException("BENCHMARK_RUN_NOT_FOUND"));if(current.status()!=BenchmarkStatus.PENDING)throw new IllegalArgumentException(current.status()==BenchmarkStatus.COMPLETED||current.status()==BenchmarkStatus.FAILED?"BENCHMARK_RUN_IMMUTABLE":"INVALID_BENCHMARK_TRANSITION");BenchmarkRun running=new BenchmarkRun(current.schemaVersion(),current.runId(),current.datasetId(),current.revisionId(),current.startedAt(),null,BenchmarkStatus.RUNNING,current.model(),current.provider(),current.datasetChecksum(),current.manifestChecksum(),null);writeRun(running);return running;}
	@Override public BenchmarkRun failRun(VisionDatasetId datasetId,String runId,BenchmarkFailure failure){BenchmarkRun current=findRun(datasetId,runId).orElseThrow(()->new IllegalArgumentException("BENCHMARK_RUN_NOT_FOUND"));if(current.status()!=BenchmarkStatus.RUNNING)throw new IllegalArgumentException(current.status()==BenchmarkStatus.COMPLETED||current.status()==BenchmarkStatus.FAILED?"BENCHMARK_RUN_IMMUTABLE":"INVALID_BENCHMARK_TRANSITION");BenchmarkRun failed=new BenchmarkRun(current.schemaVersion(),current.runId(),current.datasetId(),current.revisionId(),current.startedAt(),failure.failedAt(),BenchmarkStatus.FAILED,current.model(),current.provider(),current.datasetChecksum(),current.manifestChecksum(),null,failure);writeRun(failed);return failed;}

	@Override
	public Optional<BenchmarkRun> findRun(VisionDatasetId datasetId, String runId) {
		return read(paths.benchmarkDirectory(datasetId, runId).resolve("benchmark.yml"), BenchmarkRun.class);
	}

	@Override
	public List<BenchmarkRun> listRuns(VisionDatasetId datasetId) {
		Path directory = paths.benchmarkDirectory(datasetId);
		if (!Files.isDirectory(directory)) return List.of();
		try (var files = Files.list(directory)) {
			return files.filter(Files::isDirectory).map(path -> read(path.resolve("benchmark.yml"), BenchmarkRun.class))
					.flatMap(Optional::stream).sorted(Comparator.comparing(BenchmarkRun::runId)).toList();
		} catch (IOException exception) {
			throw new IllegalStateException("BENCHMARK_READ_FAILED", exception);
		}
	}

	@Override
	public Optional<BenchmarkRun> latestRun(VisionDatasetId datasetId) {
		return listRuns(datasetId).stream().max(Comparator.comparing(BenchmarkRun::runId));
	}

	private void writeRun(BenchmarkRun run) {
		Path target = paths.benchmarkDirectory(run.datasetId(), run.runId());
		try {
			Files.createDirectories(target);
			atomicWrite(target.resolve("benchmark.yml"), yaml.writerWithDefaultPrettyPrinter().writeValueAsBytes(run));
			if (run.status() == BenchmarkStatus.COMPLETED) {
				BenchmarkSummary summary = new BenchmarkSummary(null, run.revisionId(), run.status(), run.metrics());
				atomicWrite(target.resolve("report.md"), report(run, summary).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			}
		} catch (IOException exception) {
			throw new IllegalStateException("BENCHMARK_WRITE_FAILED", exception);
		}
	}

	private void atomicWrite(Path target, byte[] content) throws IOException {
		Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
		Files.write(temporary, content);
		try {
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private String report(BenchmarkRun run, BenchmarkSummary summary) {
		return "# Benchmark " + run.runId() + "\n\n"
				+ "- Revision: " + summary.revisionId() + "\n"
				+ "- Status: " + summary.status() + "\n"
				+ "- Model: " + run.model() + "\n"
				+ "- Provider: " + run.provider() + "\n"
				+ "- Cases: " + summary.metrics().caseCount() + "\n"
				+ "- Category accuracy: " + summary.metrics().categoryAccuracy() + "\n";
	}

	private <T> Optional<T> read(Path path, Class<T> type) {
		if (!Files.isRegularFile(path)) return Optional.empty();
		try {
			return Optional.of(yaml.readValue(path.toFile(), type));
		} catch (IOException exception) {
			throw new IllegalStateException("BENCHMARK_READ_FAILED", exception);
		}
	}
}
