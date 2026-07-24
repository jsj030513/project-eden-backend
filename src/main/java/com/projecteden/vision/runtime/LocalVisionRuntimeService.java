package com.projecteden.vision.runtime;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.projecteden.memorytaxonomy.observation.ImageObservationRequest;
import com.projecteden.vision.config.VisionModelProperties;
import com.projecteden.vision.detection.BoundingBox;
import com.projecteden.vision.detection.DetectionConfidence;
import com.projecteden.vision.detection.DetectionObject;
import com.projecteden.vision.detection.DetectionResult;
import com.projecteden.vision.yolox.YoloXDetection;
import com.projecteden.vision.yolox.YoloXImagePreprocessor;
import com.projecteden.vision.yolox.YoloXInferenceEngine;
import com.projecteden.vision.yolox.YoloXOutputDecoder;
import com.projecteden.vision.VisionRuntimeException;

/** Lazy, cached runtime boundary. It never downloads a model or fails application startup. */
@Service
public class LocalVisionRuntimeService implements LocalVisionRuntime, AutoCloseable {
	private static final Logger log = LoggerFactory.getLogger(LocalVisionRuntimeService.class);
	private final VisionModelProperties properties;
	private final YoloXImagePreprocessor preprocessor;
	private final YoloXOutputDecoder outputDecoder;
	private volatile YoloXInferenceEngine engine;
	private volatile LocalVisionRuntimeStatus status;

	public LocalVisionRuntimeService(VisionModelProperties properties, YoloXImagePreprocessor preprocessor, YoloXOutputDecoder outputDecoder) {
		this.properties = properties; this.preprocessor = preprocessor; this.outputDecoder = outputDecoder;
	}

	public synchronized boolean ready() {
		if (engine != null) return true;
		if (status != null) return false;
		if (!properties.isEnabled()) return unavailable(LocalVisionRuntimeStatus.DISABLED);
		String path = properties.getModel().getPath();
		if (path == null || path.isBlank()) return unavailable(LocalVisionRuntimeStatus.MODEL_MISSING);
		try {
			engine = new YoloXInferenceEngine(Path.of(path), properties.getModel().getSha256());
			status = LocalVisionRuntimeStatus.READY;
			return true;
		} catch (VisionRuntimeException exception) {
			return unavailable(switch (exception.getErrorCode()) {
				case MODEL_CHECKSUM_MISMATCH -> LocalVisionRuntimeStatus.CHECKSUM_MISMATCH;
				case MODEL_FILE_NOT_FOUND, MODEL_PATH_MISSING -> LocalVisionRuntimeStatus.MODEL_MISSING;
				case RUNTIME_UNAVAILABLE, NATIVE_LIBRARY_LOAD_FAILED, UNSUPPORTED_PLATFORM -> LocalVisionRuntimeStatus.RUNTIME_UNAVAILABLE;
				default -> LocalVisionRuntimeStatus.MODEL_LOAD_FAILED;
			});
		} catch (RuntimeException exception) {
			return unavailable(LocalVisionRuntimeStatus.MODEL_LOAD_FAILED);
		}
	}

	@Override
	public DetectionResult detect(ImageObservationRequest request) {
		validateNormalizedRequest(request);
		if (!ready()) return new DetectionResult(List.of(), modelVersion());
		var input = preprocessor.preprocess(request.imageBytes());
		if (!request.imageWidth().equals(input.transform().originalWidth())
				|| !request.imageHeight().equals(input.transform().originalHeight())) {
			throw new VisionRuntimeException(com.projecteden.vision.VisionRuntimeErrorCode.PREPROCESSING_FAILED,
					"정규화된 이미지 metadata가 실제 이미지와 일치하지 않습니다.", null);
		}
		float[][] output = engine.run(input);
		List<YoloXDetection> detections = outputDecoder.decode(output, input.transform(), properties.getYolox().getConfidenceThreshold(), properties.getYolox().getNmsThreshold(), properties.getYolox().getMaxDetections());
		log.info("Local vision inference completed detectionCount={} threshold={} topCandidateConfidence={} classCodes={}",
				detections.size(), properties.getYolox().getConfidenceThreshold(), outputDecoder.maxCandidateConfidence(output),
				detections.stream().map(YoloXDetection::className).distinct().toList());
		return new DetectionResult(detections.stream().map(detection -> new DetectionObject(detection.className(), new DetectionConfidence(detection.confidence()), new BoundingBox(detection.x1(), detection.y1(), detection.x2(), detection.y2()))).toList(), modelVersion());
	}

	@Override public String unavailableReason() { return status == null || status == LocalVisionRuntimeStatus.READY ? null : "LOCAL_" + status.name(); }
	public LocalVisionRuntimeStatus status() { return status == null ? LocalVisionRuntimeStatus.RUNTIME_UNAVAILABLE : status; }
	@Override public String modelVersion() { return properties.getModel().getType(); }

	private void validateNormalizedRequest(ImageObservationRequest request) {
		if (request == null || !request.isNormalizedImage() || !request.hasImageBytes()
				|| request.imageWidth() == null || request.imageHeight() == null
				|| request.imageChecksum() == null || request.imageChecksum().isBlank()
				|| !("image/jpeg".equalsIgnoreCase(request.contentType()) || "image/png".equalsIgnoreCase(request.contentType()))) {
			throw new com.projecteden.vision.VisionRuntimeException(
					com.projecteden.vision.VisionRuntimeErrorCode.PREPROCESSING_FAILED,
					"정규화된 JPEG 또는 PNG 이미지만 Local Vision에 전달할 수 있습니다.", null);
		}
		if (!sha256(request.imageBytes()).equalsIgnoreCase(request.imageChecksum())) {
			throw new VisionRuntimeException(com.projecteden.vision.VisionRuntimeErrorCode.PREPROCESSING_FAILED,
					"정규화된 이미지 checksum이 일치하지 않습니다.", null);
		}
	}
	private String sha256(byte[] bytes) {
		try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
		catch (NoSuchAlgorithmException exception) { throw new VisionRuntimeException(com.projecteden.vision.VisionRuntimeErrorCode.RUNTIME_UNAVAILABLE, "SHA-256을 사용할 수 없습니다.", exception); }
	}
	private boolean unavailable(LocalVisionRuntimeStatus reason) { status = reason; return false; }
	@PreDestroy
	@Override public synchronized void close() { if (engine != null) { engine.close(); engine = null; } if (status == LocalVisionRuntimeStatus.READY) status = null; }
}
