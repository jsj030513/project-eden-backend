package com.projecteden.dataset;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecteden.memorytaxonomy.evaluation.ImageEvaluationCase;
import com.projecteden.memorytaxonomy.evaluation.ImageEvaluationManifestReader;

@Component
@ConditionalOnProperty(prefix = "eden.dataset", name = "enabled", havingValue = "true")
public class FilesystemBenchmarkEvaluator implements BenchmarkEvaluator {

	private final DatasetPathResolver paths;
	private final ImageEvaluationManifestReader manifestReader;
	private final BenchmarkManager benchmarks;

	public FilesystemBenchmarkEvaluator(@Value("${eden.dataset.root:}") String configuredRoot, ObjectMapper objectMapper, BenchmarkManager benchmarks) {
		this(configuredRoot == null || configuredRoot.isBlank()
				? Path.of(System.getenv().getOrDefault("EDEN_DATASET_ROOT", System.getProperty("user.home") + "/.project-eden/datasets"))
				: Path.of(configuredRoot), objectMapper, benchmarks);
	}

	FilesystemBenchmarkEvaluator(Path root, ObjectMapper objectMapper, BenchmarkManager benchmarks) {
		this.paths = new DatasetPathResolver(root);
		this.manifestReader = new ImageEvaluationManifestReader(objectMapper);
		this.benchmarks = benchmarks;
	}

	@Override
	public EvaluationResult evaluate(VisionDatasetId datasetId, String revisionId, Map<String, VisionGroundTruth> predictions) {
		Path revision = paths.revisionDirectory(datasetId, revisionId);
		if (!Files.isRegularFile(revision.resolve("revision.yml"))) throw new IllegalArgumentException("REVISION_NOT_FOUND");
		Path manifest = revision.resolve("manifest.yml");
		if (!Files.isRegularFile(manifest)) throw new IllegalArgumentException("REVISION_MANIFEST_NOT_FOUND");
		Instant startedAt = Instant.now();
		List<ImageEvaluationCase> expected = manifestReader.read(manifest, Integer.MAX_VALUE);
		List<EvaluationCase> cases = new ArrayList<>();
		for (ImageEvaluationCase item : expected) {
			VisionGroundTruth prediction = predictions == null ? null : predictions.get(item.caseId());
			if (prediction == null) throw new IllegalArgumentException("PREDICTION_NOT_FOUND: " + item.caseId());
			VisionGroundTruth groundTruth = groundTruth(item);
			cases.add(evaluateCase(item.caseId(), prediction, groundTruth));
		}
		return new EvaluationResult(metrics(cases), new EvaluationProgress(cases.size(), cases.size(), 0, startedAt, Instant.now()), cases);
	}

	@Override
	public EvaluationResult evaluateRevision(VisionDatasetId datasetId, String revisionId, Map<String, VisionGroundTruth> predictions) {
		return evaluate(datasetId, revisionId, predictions);
	}

	@Override
	public EvaluationCase evaluateCase(String caseId, VisionGroundTruth prediction, VisionGroundTruth groundTruth) {
		if (prediction == null) throw new IllegalArgumentException("PREDICTION_NOT_FOUND");
		if (groundTruth == null) throw new IllegalArgumentException("GROUND_TRUTH_NOT_FOUND");
		return new EvaluationCase(caseId, prediction, groundTruth, groundTruth, EvaluationCaseStatus.COMPLETED);
	}

	@Override
	public BenchmarkRun evaluateAndFinish(VisionDatasetId datasetId, String revisionId, String runId, Map<String, VisionGroundTruth> predictions) {
		BenchmarkRun run = benchmarks.findRun(datasetId, runId).orElseThrow(() -> new IllegalArgumentException("BENCHMARK_RUN_NOT_FOUND"));
		if (!run.revisionId().equals(revisionId)) throw new IllegalArgumentException("BENCHMARK_REVISION_MISMATCH");
		return benchmarks.finishRun(datasetId, runId, evaluateRevision(datasetId, revisionId, predictions).metrics());
	}

	private VisionGroundTruth groundTruth(ImageEvaluationCase item) {
		return new VisionGroundTruth(item.expectedObjects(), item.expectedActivities(), item.expectedRelationships(), item.expectedPrimary(),
				item.expectedSecondary(), item.expectedTags(), Boolean.TRUE.equals(item.expectedFallback()));
	}

	private BenchmarkMetrics metrics(List<EvaluationCase> cases) {
		int total = cases.size();
		int categoryCorrect = 0;
		int fallback = 0;
		int unknown = 0;
		Counts tags = new Counts();
		Counts objects = new Counts();
		Counts activities = new Counts();
		Counts relationships = new Counts();
		for (EvaluationCase item : cases) {
			VisionGroundTruth prediction = item.prediction();
			VisionGroundTruth expected = item.groundTruth();
			if (Objects.equals(prediction.category(), expected.category())) categoryCorrect++;
			if (prediction.fallback()) fallback++;
			if (!prediction.fallback() && prediction.category() == null) unknown++;
			tags.add(prediction.tags(), expected.tags());
			objects.add(prediction.objects(), expected.objects());
			activities.add(prediction.activities(), expected.activities());
			relationships.add(prediction.relationships(), expected.relationships());
		}
		return new BenchmarkMetrics(rate(categoryCorrect, total), tags.precision(), tags.recall(), f1(tags), objects.precision(), objects.recall(),
				activities.precision(), activities.recall(), relationships.precision(), relationships.recall(), rate(fallback, total), rate(unknown, total), total);
	}

	private BigDecimal f1(Counts counts) {
		BigDecimal precision = counts.precision();
		BigDecimal recall = counts.recall();
		return precision.add(recall).signum() == 0 ? BigDecimal.ZERO : precision.multiply(recall).multiply(BigDecimal.valueOf(2)).divide(precision.add(recall), 6, RoundingMode.HALF_UP);
	}

	private BigDecimal rate(int numerator, int denominator) {
		return denominator == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
	}

	private final class Counts {
		private int truePositive;
		private int predicted;
		private int expected;
		void add(List<String> actual, List<String> wanted) {
			Set<String> actualSet = new HashSet<>(actual);
			Set<String> wantedSet = new HashSet<>(wanted);
			predicted += actualSet.size();
			expected += wantedSet.size();
			actualSet.retainAll(wantedSet);
			truePositive += actualSet.size();
		}
		BigDecimal precision() { return rate(truePositive, predicted); }
		BigDecimal recall() { return rate(truePositive, expected); }
	}
}
