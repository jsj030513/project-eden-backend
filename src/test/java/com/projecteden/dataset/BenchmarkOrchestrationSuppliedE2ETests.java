package com.projecteden.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.projecteden.imagenormalization.ImageFormat;
import com.projecteden.imagenormalization.ImageNormalizationService;
import com.projecteden.imagenormalization.NormalizedImage;

class BenchmarkOrchestrationSuppliedE2ETests {
	@TempDir Path root;

	@Test void suppliedPredictionsCompleteRealFilesystemRun() throws Exception {
		Fixture fixture = fixture();
		byte[] revisionManifest = Files.readAllBytes(fixture.revisionDirectory.resolve("manifest.yml"));
		byte[] revisionImage = Files.readAllBytes(fixture.revisionDirectory.resolve("images/cat-001.jpg"));
		Map<String,String> before = FilesystemAuditSupport.snapshotChecksums(fixture.root.resolve("datasets/eden-local"));
		BenchmarkExecutionResult result = fixture.orchestrator.execute(request(Map.of("cat-001", fixture.truth)));
		Path runDirectory = fixture.root.resolve("datasets/eden-local/benchmarks/").resolve(result.run().runId());
		assertThat(result.run().status()).isEqualTo(BenchmarkStatus.COMPLETED);
		assertThat(result.run().failure()).isNull(); assertThat(result.run().metrics()).isNotNull();
		assertThat(Files.exists(runDirectory.resolve("benchmark.yml"))).isTrue(); assertThat(Files.exists(runDirectory.resolve("quality-gate.yml"))).isTrue(); assertThat(Files.exists(runDirectory.resolve("report.md"))).isTrue();
		BenchmarkRun persisted = fixture.benchmarks.findRun(fixture.datasetId, result.run().runId()).orElseThrow();
		assertThat(persisted.status()).isEqualTo(BenchmarkStatus.COMPLETED); assertThat(persisted.revisionId()).isEqualTo(fixture.revision.revisionId());
		assertThat(Files.readString(runDirectory.resolve("report.md"))).contains("## Quality Gate").doesNotContain(root.toString(), "Exception", "base64");
		assertThat(Files.readAllBytes(fixture.revisionDirectory.resolve("manifest.yml"))).isEqualTo(revisionManifest);
		assertThat(Files.readAllBytes(fixture.revisionDirectory.resolve("images/cat-001.jpg"))).isEqualTo(revisionImage);
		assertThat(FilesystemAuditSupport.snapshotChecksums(fixture.root.resolve("datasets/eden-local"))).isEqualTo(before);
		assertThat(FilesystemAuditSupport.findTemporaryEntries(fixture.root.resolve("datasets/eden-local"))).isEmpty();
	}

	@Test void suppliedPredictionMissingRealFilesystemFailure() throws Exception {
		Fixture fixture = fixture();
		Path dataset=fixture.root.resolve("datasets/eden-local"); Map<String,String> before=FilesystemAuditSupport.snapshotChecksums(dataset); java.util.Set<String> beforePaths=FilesystemAuditSupport.snapshotRelativePaths(dataset);
		assertThatThrownBy(() -> fixture.orchestrator.execute(request(Map.of()))).hasMessageContaining("PREDICTION_NOT_FOUND");
		BenchmarkRun first = fixture.benchmarks.latestRun(fixture.datasetId).orElseThrow();
		assertThat(first.status()).isEqualTo(BenchmarkStatus.FAILED); assertThat(first.failure().code()).isEqualTo(BenchmarkFailureCode.PREDICTION_MISSING); assertThat(first.metrics()).isNull();
		Path firstDirectory = fixture.root.resolve("datasets/eden-local/benchmarks/").resolve(first.runId());
		BenchmarkQualityGateResult gate = yaml().readValue(firstDirectory.resolve("quality-gate.yml").toFile(), BenchmarkQualityGateResult.class);
		assertThat(gate.decision()).isEqualTo(BenchmarkQualityDecision.EVALUATION_FAILED);
		assertThat(Files.readString(firstDirectory.resolve("report.md"))).contains("## Failure", "## Quality Gate").doesNotContain(root.toString(), "Exception", "base64", "Bearer");
		Map<String,String> firstChecksums=FilesystemAuditSupport.snapshotChecksumsIncludingBenchmarks(firstDirectory);
		assertThatThrownBy(() -> fixture.orchestrator.execute(request(Map.of()))).hasMessageContaining("PREDICTION_NOT_FOUND");
		BenchmarkRun second = fixture.benchmarks.latestRun(fixture.datasetId).orElseThrow();
		assertThat(second.runId()).isNotEqualTo(first.runId()); assertThat(second.status()).isEqualTo(BenchmarkStatus.FAILED);
		assertThat(FilesystemAuditSupport.snapshotChecksums(dataset)).isEqualTo(before); assertThat(FilesystemAuditSupport.snapshotChecksumsIncludingBenchmarks(firstDirectory)).isEqualTo(firstChecksums);
		java.util.Set<String> added=FilesystemAuditSupport.snapshotRelativePaths(dataset); added.removeAll(beforePaths); assertThat(added).allMatch(path->path.startsWith("benchmarks/"+first.runId()+"/")||path.startsWith("benchmarks/"+second.runId()+"/"));
		assertThat(FilesystemAuditSupport.findTemporaryEntries(dataset)).isEmpty();
		assertThat(Files.list(firstDirectory).filter(Files::isRegularFile).map(path->path.getFileName().toString()).toList()).containsExactlyInAnyOrder("benchmark.yml","quality-gate.yml","report.md");
	}

	private Fixture fixture() throws Exception {
		ImageNormalizationService normalization = input -> new NormalizedImage(input.bytes(), "image/jpeg", ImageFormat.JPEG, 2, 2, ImageFormat.JPEG, 2, 2, false, false, false, false, false, true, "fixture");
		FilesystemVisionDatasetManager datasets = new FilesystemVisionDatasetManager(normalization, new ObjectMapper(), root);
		FilesystemReviewQueueManager reviews = new FilesystemReviewQueueManager(root); VisionDatasetId id = new VisionDatasetId("eden-local"); VisionGroundTruth truth = new VisionGroundTruth(List.of("CAT"), List.of(), List.of(), "ANIMAL", List.of(), List.of("CAT"), false);
		datasets.createDataset(new VisionDataset(null, id, "Eden", 1, "ACTIVE", 0)); Path source=root.resolve("cat.jpg"); Files.write(source, new byte[]{1,2,3});
		datasets.importCase(id, new VisionDatasetCase(null,new VisionDatasetCaseId("cat-001"),id,null,null,0,0,null,new VisionConsentMetadata(true,true,false,false,Instant.EPOCH,"v1"),truth,"PENDING",null),source);
		ReviewItem review=reviews.enqueue(id,new VisionDatasetCaseId("cat-001"),truth,"reviewer",null); reviews.approve(id,review.reviewId(),"reviewer","approved"); datasets.exportManifest(id);
		FilesystemDatasetVersionManager versions=new FilesystemDatasetVersionManager(root); DatasetRevision revision=versions.createRevision(id,new RevisionMetadata("reviewer","fixture"));
		FilesystemBenchmarkManager benchmarks=new FilesystemBenchmarkManager(root); FilesystemBenchmarkEvaluator evaluator=new FilesystemBenchmarkEvaluator(root,new ObjectMapper(),benchmarks); FilesystemQualityGateStore store=new FilesystemQualityGateStore(root.toString());
		BenchmarkPredictionSourceResolver resolver=request -> new SuppliedBenchmarkPredictionSource(request.suppliedPredictions());
		return new Fixture(root,id,truth,revision,root.resolve("datasets/eden-local/revisions/").resolve(revision.revisionId()),benchmarks,new FilesystemBenchmarkOrchestrator(benchmarks,evaluator,store,resolver));
	}
	private BenchmarkExecutionRequest request(Map<String,VisionGroundTruth> predictions){return new BenchmarkExecutionRequest(new VisionDatasetId("eden-local"),"rev-000001","fixture-model","supplied",BenchmarkPredictionSourceType.SUPPLIED,predictions,"tester",null);}
	private ObjectMapper yaml(){return new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());}
	private record Fixture(Path root,VisionDatasetId datasetId,VisionGroundTruth truth,DatasetRevision revision,Path revisionDirectory,FilesystemBenchmarkManager benchmarks,FilesystemBenchmarkOrchestrator orchestrator) { }
}
