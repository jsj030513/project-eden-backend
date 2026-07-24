package com.projecteden.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class FilesystemBenchmarkEvaluatorTests {

	@TempDir
	Path root;

	@Test
	void evaluatesRevisionManifestAndFinishesBenchmarkWithoutInference() throws Exception {
		VisionDatasetId datasetId = new VisionDatasetId("eden-local");
		writeRevision(datasetId, "rev-000001");
		FilesystemBenchmarkManager benchmarks = new FilesystemBenchmarkManager(root);
		BenchmarkRun run = benchmarks.createRun(datasetId, "rev-000001", "fixture-model", "fixture-provider");
		FilesystemBenchmarkEvaluator evaluator = new FilesystemBenchmarkEvaluator(root, new ObjectMapper(), benchmarks);
		Map<String, VisionGroundTruth> predictions = Map.of(
				"case-1", truth("ANIMAL", List.of("CAT"), List.of("CAT"), List.of("WALKING"), List.of("FRIENDS"), false),
				"case-2", truth("FOOD", List.of("FOOD"), List.of("FOOD"), List.of(), List.of(), false),
				"case-3", truth(null, List.of(), List.of(), List.of(), List.of(), false));

		EvaluationResult result = evaluator.evaluateRevision(datasetId, "rev-000001", predictions);
		assertThat(result.progress().total()).isEqualTo(3);
		assertThat(result.progress().processed()).isEqualTo(3);
		assertThat(result.metrics().categoryAccuracy()).isEqualByComparingTo("0.666667");
		assertThat(result.metrics().tagPrecision()).isEqualByComparingTo("1.0");
		assertThat(result.metrics().tagRecall()).isEqualByComparingTo("0.666667");
		assertThat(result.metrics().tagF1()).isEqualByComparingTo("0.8");
		assertThat(result.metrics().activityPrecision()).isEqualByComparingTo("1.0");
		assertThat(result.metrics().activityRecall()).isEqualByComparingTo("0.5");
		assertThat(result.metrics().relationshipPrecision()).isEqualByComparingTo("1.0");
		assertThat(result.metrics().relationshipRecall()).isEqualByComparingTo("1.0");
		assertThat(result.metrics().fallbackRate()).isEqualByComparingTo("0.0");
		assertThat(result.metrics().unknownRate()).isEqualByComparingTo("0.333333");

		BenchmarkRun completed = evaluator.evaluateAndFinish(datasetId, "rev-000001", run.runId(), predictions);
		assertThat(completed.status()).isEqualTo(BenchmarkStatus.COMPLETED);
		assertThat(completed.metrics()).isEqualTo(result.metrics());
		assertThat(Files.exists(root.resolve("datasets/eden-local/benchmarks/run-000001/report.md"))).isTrue();
	}

	@Test
	void rejectsMissingRevisionManifestAndPrediction() throws Exception {
		FilesystemBenchmarkEvaluator evaluator = new FilesystemBenchmarkEvaluator(root, new ObjectMapper(), new FilesystemBenchmarkManager(root));
		VisionDatasetId datasetId = new VisionDatasetId("eden-local");
		assertThatThrownBy(() -> evaluator.evaluateRevision(datasetId, "rev-000001", Map.of()))
				.hasMessageContaining("REVISION_NOT_FOUND");

		writeRevision(datasetId, "rev-000001");
		assertThatThrownBy(() -> evaluator.evaluateRevision(datasetId, "rev-000001", Map.of()))
				.hasMessageContaining("PREDICTION_NOT_FOUND: case-1");
	}

	private void writeRevision(VisionDatasetId datasetId, String revisionId) throws Exception {
		Path revision = root.resolve("datasets/eden-local/revisions").resolve(revisionId);
		Files.createDirectories(revision);
		yaml().writerWithDefaultPrettyPrinter().writeValue(revision.resolve("revision.yml").toFile(),
				new DatasetRevision(null, revisionId, datasetId, Instant.EPOCH, new RevisionMetadata(null, null), 3,
						"manifest-checksum", "dataset-checksum", "summary-checksum", RevisionStatus.ACTIVE));
		Files.writeString(revision.resolve("manifest.yml"), """
				version: 2
				cases:
				  - caseId: case-1
				    enabled: true
				    imagePath: image-1.jpg
				    expectedPrimary: ANIMAL
				    expectedTags: [CAT]
				    expectedObjects: [CAT]
				    expectedActivities: [WALKING]
				    expectedRelationships: [FRIENDS]
				    expectedFallback: false
				  - caseId: case-2
				    enabled: true
				    imagePath: image-2.jpg
				    expectedPrimary: FOOD
				    expectedTags: [FOOD]
				    expectedObjects: [FOOD]
				    expectedActivities: [EATING]
				    expectedRelationships: []
				    expectedFallback: false
				  - caseId: case-3
				    enabled: true
				    imagePath: image-3.jpg
				    expectedPrimary: WATER
				    expectedTags: [DOG]
				    expectedObjects: [DOG]
				    expectedActivities: []
				    expectedRelationships: []
				    expectedFallback: false
				""");
	}

	private VisionGroundTruth truth(String category, List<String> tags, List<String> objects, List<String> activities, List<String> relationships, boolean fallback) {
		return new VisionGroundTruth(objects, activities, relationships, category, tags, fallback);
	}

	private ObjectMapper yaml() {
		return new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());
	}
}
