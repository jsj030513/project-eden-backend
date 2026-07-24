package com.projecteden.dataset;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.projecteden.imagenormalization.ImageNormalizationService;
import com.projecteden.memorytaxonomy.classification.MemoryClassificationService;
import com.projecteden.memorytaxonomy.evaluation.ImageEvaluationManifestReader;
import com.projecteden.memorytaxonomy.observation.MockImageObservationProvider;
import com.projecteden.vision.observation.LocalImageObservationProvider;
import com.projecteden.vision.runtime.LocalVisionRuntime;

@Component
@ConditionalOnProperty(prefix = "eden.benchmark.orchestration", name = "enabled", havingValue = "true")
public class FilesystemBenchmarkPredictionSourceResolver implements BenchmarkPredictionSourceResolver {
	private final Path root; private final ImageEvaluationManifestReader manifests; private final MockImageObservationProvider mock;
	private final LocalImageObservationProvider local; private final LocalVisionRuntime runtime; private final ImageNormalizationService normalization; private final MemoryClassificationService classification;
	public FilesystemBenchmarkPredictionSourceResolver(@Value("${eden.dataset.root:}") String configuredRoot, ImageEvaluationManifestReader manifests, MockImageObservationProvider mock, LocalImageObservationProvider local, LocalVisionRuntime runtime, ImageNormalizationService normalization, MemoryClassificationService classification) {
		this.root = configuredRoot == null || configuredRoot.isBlank() ? Path.of(System.getenv().getOrDefault("EDEN_DATASET_ROOT", System.getProperty("user.home") + "/.project-eden/datasets")) : Path.of(configuredRoot);
		this.manifests=manifests; this.mock=mock; this.local=local; this.runtime=runtime; this.normalization=normalization; this.classification=classification;
	}
	@Override
	public BenchmarkPredictionSource resolve(BenchmarkExecutionRequest request) {
		if (request.predictionSource() == BenchmarkPredictionSourceType.SUPPLIED) return new SuppliedBenchmarkPredictionSource(request.suppliedPredictions());
		if (request.predictionSource() == BenchmarkPredictionSourceType.LOCAL && !runtime.ready()) throw new IllegalArgumentException("LOCAL_PROVIDER_DISABLED");
		if (request.predictionSource() != BenchmarkPredictionSourceType.MOCK && request.predictionSource() != BenchmarkPredictionSourceType.LOCAL) throw new IllegalArgumentException("PREDICTION_SOURCE_UNSUPPORTED");
		Map<String, String> images = new LinkedHashMap<>();
		manifests.read(new DatasetPathResolver(root).revisionDirectory(request.datasetId(), request.revisionId()).resolve("manifest.yml"), Integer.MAX_VALUE).forEach(item -> images.put(item.caseId(), item.imagePath()));
		return new ObservationBenchmarkPredictionSource(request.predictionSource(), new RevisionImageResolver(root), request.predictionSource() == BenchmarkPredictionSourceType.MOCK ? mock : local, normalization, classification, images);
	}
}
