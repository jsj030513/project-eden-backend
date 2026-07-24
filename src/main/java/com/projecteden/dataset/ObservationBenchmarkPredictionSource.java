package com.projecteden.dataset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.projecteden.imagenormalization.ImageNormalizationService;
import com.projecteden.memorytaxonomy.classification.MemoryClassificationResult;
import com.projecteden.memorytaxonomy.classification.MemoryClassificationService;
import com.projecteden.memorytaxonomy.observation.ImageObservation;
import com.projecteden.memorytaxonomy.observation.ImageObservationProvider;
import com.projecteden.memorytaxonomy.observation.ImageObservationRequest;
import com.projecteden.memorytaxonomy.observation.UploadedImagePayload;

final class ObservationBenchmarkPredictionSource implements BenchmarkPredictionSource {
	private final BenchmarkPredictionSourceType type;
	private final RevisionImageResolver images;
	private final ImageObservationProvider provider;
	private final ImageNormalizationService normalization;
	private final MemoryClassificationService classification;
	private final Map<String, String> caseImages;

	ObservationBenchmarkPredictionSource(BenchmarkPredictionSourceType type, RevisionImageResolver images, ImageObservationProvider provider,
			ImageNormalizationService normalization, MemoryClassificationService classification, Map<String, String> caseImages) {
		this.type = type; this.images = images; this.provider = provider; this.normalization = normalization; this.classification = classification; this.caseImages = Map.copyOf(caseImages);
	}
	@Override public BenchmarkPredictionSourceType type() { return type; }
	@Override public Map<String, VisionGroundTruth> predict(VisionDatasetId datasetId, String revisionId) {
		Map<String, VisionGroundTruth> result = new LinkedHashMap<>();
		for (Map.Entry<String, String> item : caseImages.entrySet()) result.put(item.getKey(), predictOne(images.resolve(datasetId, revisionId, item.getValue())));
		return result;
	}
	private VisionGroundTruth predictOne(Path image) {
		try {
			byte[] bytes = Files.readAllBytes(image);
			String contentType = image.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png") ? "image/png" : "image/jpeg";
			ImageObservationRequest request = ImageObservationRequest.of(null, image.getFileName().toString(), contentType, bytes.length, bytes);
			if (type == BenchmarkPredictionSourceType.LOCAL) request = ImageObservationRequest.normalized(null, image.getFileName().toString(), normalization.normalize(UploadedImagePayload.of(image.getFileName().toString(), contentType, bytes.length, bytes)));
			ImageObservation observation = provider.observe(request);
			MemoryClassificationResult classified = classification.classify(observation);
			return new VisionGroundTruth(observation.objects(), observation.activities(), observation.relationships(), classified.primaryCategory(), classified.secondaryCategories(), classified.tags(), classified.fallback());
		} catch (IOException exception) { throw new IllegalArgumentException("REVISION_IMAGE_READ_FAILED", exception); }
	}
}
