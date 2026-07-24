package com.projecteden.vision.yolox;

import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import com.projecteden.vision.VisionRuntimeErrorCode;
import com.projecteden.vision.VisionRuntimeException;
import com.projecteden.vision.model.VisionModelIntegrity;

public final class YoloXInferenceEngine implements AutoCloseable {
	private final OrtSession session;
	private final String inputName;

	public YoloXInferenceEngine(Path modelPath, String expectedSha256) {
		String actual = VisionModelIntegrity.sha256(modelPath);
		if (expectedSha256 == null || expectedSha256.isBlank() || !actual.equalsIgnoreCase(expectedSha256)) throw new VisionRuntimeException(VisionRuntimeErrorCode.MODEL_CHECKSUM_MISMATCH,"모델 checksum이 일치하지 않습니다.",null);
		try { session=OrtEnvironment.getEnvironment().createSession(modelPath.toString()); inputName=session.getInputNames().stream().findFirst().orElseThrow(); }
		catch(Exception exception) { throw new VisionRuntimeException(VisionRuntimeErrorCode.MODEL_LOAD_FAILED,"YOLOX 모델을 로드할 수 없습니다.",exception); }
	}

	public float[][] run(YoloXPreprocessResult input) {
		try (OnnxTensor tensor=OnnxTensor.createTensor(OrtEnvironment.getEnvironment(),FloatBuffer.wrap(input.tensorData()),new long[]{1,3,416,416}); OrtSession.Result result=session.run(Map.of(inputName,tensor))) {
			Object value=result.get(0).getValue();
			if (!(value instanceof float[][][] batch) || batch.length!=1) throw new VisionRuntimeException(VisionRuntimeErrorCode.OUTPUT_CONTRACT_MISMATCH,"YOLOX 출력 tensor 형식이 올바르지 않습니다.",null);
			return batch[0];
		} catch(VisionRuntimeException exception) { throw exception; }
		catch(Exception exception) { throw new VisionRuntimeException(VisionRuntimeErrorCode.INFERENCE_FAILED,"YOLOX 추론을 실행할 수 없습니다.",exception); }
	}
	public void close() { try { session.close(); } catch(Exception exception) { throw new VisionRuntimeException(VisionRuntimeErrorCode.RESOURCE_CLOSE_FAILED,"YOLOX session을 닫을 수 없습니다.",exception); } }
}
