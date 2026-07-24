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
import com.projecteden.imagenormalization.ImageFormat;
import com.projecteden.imagenormalization.ImageNormalizationService;
import com.projecteden.imagenormalization.NormalizedImage;
import com.projecteden.memorytaxonomy.observation.UploadedImagePayload;

class BenchmarkOrchestrationObservationE2ETests {
	@TempDir Path root;

	@Test void mockPredictionRunsFromRevisionImageAfterLiveImageDeletionDeterministically() throws Exception {
		Fixture fixture = fixture();
		Files.delete(root.resolve("datasets/eden-local/cases/cat-001/image.jpg"));
		Path dataset=root.resolve("datasets/eden-local"); Map<String,String> before=FilesystemAuditSupport.snapshotChecksums(dataset); java.util.Set<String> beforePaths=FilesystemAuditSupport.snapshotRelativePaths(dataset);
		BenchmarkExecutionResult first = fixture.orchestrator.execute(request());
		Path firstDirectory=root.resolve("datasets/eden-local/benchmarks/").resolve(first.run().runId()); Map<String,String> firstChecksums=FilesystemAuditSupport.snapshotChecksumsIncludingBenchmarks(firstDirectory);
		BenchmarkExecutionResult second = fixture.orchestrator.execute(request());
		assertThat(first.run().status()).isEqualTo(BenchmarkStatus.COMPLETED); assertThat(first.run().failure()).isNull(); assertThat(first.run().metrics()).isNotNull();
		assertThat(second.run().runId()).isNotEqualTo(first.run().runId()); assertThat(second.run().metrics()).isEqualTo(first.run().metrics());
		Path directory=firstDirectory;
		assertThat(Files.exists(directory.resolve("benchmark.yml"))).isTrue(); assertThat(Files.exists(directory.resolve("quality-gate.yml"))).isTrue(); assertThat(Files.readString(directory.resolve("report.md"))).contains("## Quality Gate").doesNotContain(root.toString(),"Exception","base64");
		BenchmarkRun persisted=fixture.benchmarks.findRun(fixture.id,first.run().runId()).orElseThrow(); assertThat(persisted.status()).isEqualTo(BenchmarkStatus.COMPLETED); assertThat(persisted.metrics()).isEqualTo(first.run().metrics());
		assertThat(FilesystemAuditSupport.snapshotChecksums(dataset)).isEqualTo(before); assertThat(FilesystemAuditSupport.snapshotChecksumsIncludingBenchmarks(firstDirectory)).isEqualTo(firstChecksums);
		java.util.Set<String> added=FilesystemAuditSupport.snapshotRelativePaths(dataset); added.removeAll(beforePaths); assertThat(added).allMatch(path->path.startsWith("benchmarks/"+first.run().runId()+"/")||path.startsWith("benchmarks/"+second.run().runId()+"/"));
		assertThat(Files.list(firstDirectory).filter(Files::isRegularFile).map(path->path.getFileName().toString()).toList()).containsExactlyInAnyOrder("benchmark.yml","quality-gate.yml","report.md");
		assertThat(FilesystemAuditSupport.findTemporaryEntries(dataset)).isEmpty();
	}

	@Test void localDisabledRealFilesystemRunPersistsPlatformUnverifiedFailure() throws Exception {
		Fixture fixture=fixture(); Files.delete(root.resolve("datasets/eden-local/cases/cat-001/image.jpg")); Path dataset=root.resolve("datasets/eden-local"); Map<String,String> before=FilesystemAuditSupport.snapshotChecksums(dataset); java.util.Set<String> beforePaths=FilesystemAuditSupport.snapshotRelativePaths(dataset);
		boolean[] evidence=new boolean[3];
		BenchmarkPredictionSource local=new BenchmarkPredictionSource(){ public BenchmarkPredictionSourceType type(){return BenchmarkPredictionSourceType.LOCAL;} public Map<String,VisionGroundTruth> predict(VisionDatasetId ignored,String revision){try{Path image=new RevisionImageResolver(root).resolve(fixture.id,"rev-000001","images/cat-001.jpg"); evidence[0]=Files.isRegularFile(image); byte[] bytes=Files.readAllBytes(image); fixture.normalization.normalize(UploadedImagePayload.of("cat-001.jpg","image/jpeg",bytes.length,bytes)); evidence[1]=true; evidence[2]=true; throw new IllegalArgumentException("LOCAL_PROVIDER_DISABLED");}catch(java.io.IOException e){throw new IllegalArgumentException("REVISION_IMAGE_NOT_FOUND",e);}}};
		FilesystemBenchmarkOrchestrator orchestrator=new FilesystemBenchmarkOrchestrator(fixture.benchmarks,fixture.evaluator,fixture.store,request->local);
		assertThatThrownBy(() -> orchestrator.execute(new BenchmarkExecutionRequest(fixture.id,"rev-000001","local","local",BenchmarkPredictionSourceType.LOCAL,Map.of(),"tester",null))).hasMessageContaining("LOCAL_PROVIDER_DISABLED");
		BenchmarkRun run=fixture.benchmarks.latestRun(fixture.id).orElseThrow(); Path directory=root.resolve("datasets/eden-local/benchmarks/").resolve(run.runId());
		assertThat(evidence).containsExactly(true,true,true); assertThat(run.status()).isEqualTo(BenchmarkStatus.FAILED); assertThat(run.failure().code()).isEqualTo(BenchmarkFailureCode.LOCAL_PROVIDER_DISABLED); assertThat(run.metrics()).isNull();
		BenchmarkQualityGateResult gate=new com.fasterxml.jackson.databind.ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory()).registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()).readValue(directory.resolve("quality-gate.yml").toFile(),BenchmarkQualityGateResult.class);
		assertThat(gate.decision()).isEqualTo(BenchmarkQualityDecision.PLATFORM_UNVERIFIED); assertThat(Files.readString(directory.resolve("report.md"))).contains("## Failure","## Quality Gate","LOCAL_PROVIDER_DISABLED","PLATFORM_UNVERIFIED").doesNotContain(root.toString(),"Exception","base64");
		assertThat(FilesystemAuditSupport.snapshotChecksums(dataset)).isEqualTo(before); java.util.Set<String> afterPaths=FilesystemAuditSupport.snapshotRelativePaths(dataset); java.util.Set<String> added=new java.util.TreeSet<>(afterPaths); added.removeAll(beforePaths); java.util.Set<String> removed=new java.util.TreeSet<>(beforePaths); removed.removeAll(afterPaths); assertThat(removed).isEmpty(); assertThat(added).allMatch(path->path.startsWith("benchmarks/"+run.runId()+"/"));
		assertThat(Files.list(directory).filter(Files::isRegularFile).map(path->path.getFileName().toString()).toList()).containsExactlyInAnyOrder("benchmark.yml","quality-gate.yml","report.md"); assertThat(FilesystemAuditSupport.findTemporaryEntries(dataset)).isEmpty();
	}

	private Fixture fixture() throws Exception {
		ImageNormalizationService normalization=input -> new NormalizedImage(input.bytes(),"image/jpeg",ImageFormat.JPEG,2,2,ImageFormat.JPEG,2,2,false,false,false,false,false,true,"fixture");
		FilesystemVisionDatasetManager datasets=new FilesystemVisionDatasetManager(normalization,new ObjectMapper(),root); FilesystemReviewQueueManager reviews=new FilesystemReviewQueueManager(root); VisionDatasetId id=new VisionDatasetId("eden-local"); VisionGroundTruth truth=new VisionGroundTruth(List.of("CAT"),List.of(),List.of(),"ANIMAL",List.of(),List.of("CAT"),false);
		datasets.createDataset(new VisionDataset(null,id,"Eden",1,"ACTIVE",0)); Path source=root.resolve("cat.jpg"); Files.write(source,new byte[]{1,2,3}); datasets.importCase(id,new VisionDatasetCase(null,new VisionDatasetCaseId("cat-001"),id,null,null,0,0,null,new VisionConsentMetadata(true,true,false,false,Instant.EPOCH,"v1"),truth,"PENDING",null),source);
		ReviewItem review=reviews.enqueue(id,new VisionDatasetCaseId("cat-001"),truth,"reviewer",null); reviews.approve(id,review.reviewId(),"reviewer","approved"); datasets.exportManifest(id); DatasetRevision revision=new FilesystemDatasetVersionManager(root).createRevision(id,new RevisionMetadata("reviewer","fixture"));
		FilesystemBenchmarkManager benchmarks=new FilesystemBenchmarkManager(root); FilesystemBenchmarkEvaluator evaluator=new FilesystemBenchmarkEvaluator(root,new ObjectMapper(),benchmarks); FilesystemQualityGateStore store=new FilesystemQualityGateStore(root.toString());
		BenchmarkPredictionSource sourceResolver=new RevisionOnlyMockSource(root,id,revision.revisionId(),normalization,truth); BenchmarkPredictionSourceResolver resolver=request -> sourceResolver;
		return new Fixture(id,benchmarks,evaluator,store,normalization,new FilesystemBenchmarkOrchestrator(benchmarks,evaluator,store,resolver));
	}
	private BenchmarkExecutionRequest request(){return new BenchmarkExecutionRequest(new VisionDatasetId("eden-local"),"rev-000001","mock","mock",BenchmarkPredictionSourceType.MOCK,Map.of(),"tester",null);}
	private record Fixture(VisionDatasetId id,FilesystemBenchmarkManager benchmarks,FilesystemBenchmarkEvaluator evaluator,FilesystemQualityGateStore store,ImageNormalizationService normalization,FilesystemBenchmarkOrchestrator orchestrator){}
	private static final class RevisionOnlyMockSource implements BenchmarkPredictionSource {
		private final RevisionImageResolver images; private final VisionDatasetId id; private final String revision; private final ImageNormalizationService normalization; private final VisionGroundTruth prediction;
		RevisionOnlyMockSource(Path root,VisionDatasetId id,String revision,ImageNormalizationService normalization,VisionGroundTruth prediction){this.images=new RevisionImageResolver(root);this.id=id;this.revision=revision;this.normalization=normalization;this.prediction=prediction;}
		public BenchmarkPredictionSourceType type(){return BenchmarkPredictionSourceType.MOCK;}
		public Map<String,VisionGroundTruth> predict(VisionDatasetId ignored,String ignoredRevision){try{Path image=images.resolve(id,revision,"images/cat-001.jpg"); byte[] bytes=Files.readAllBytes(image); normalization.normalize(UploadedImagePayload.of("cat-001.jpg","image/jpeg",bytes.length,bytes)); return Map.of("cat-001",prediction);}catch(Exception e){throw new IllegalArgumentException("REVISION_IMAGE_NOT_FOUND",e);}}
	}
}
