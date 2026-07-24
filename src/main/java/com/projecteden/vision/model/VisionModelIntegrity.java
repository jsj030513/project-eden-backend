package com.projecteden.vision.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.projecteden.vision.VisionRuntimeErrorCode;
import com.projecteden.vision.VisionRuntimeException;

public final class VisionModelIntegrity {

	private VisionModelIntegrity() {
	}

	public static String sha256(Path modelPath) {
		if (modelPath == null || !Files.isRegularFile(modelPath)) {
			throw new VisionRuntimeException(VisionRuntimeErrorCode.MODEL_FILE_NOT_FOUND,
					"모델 파일을 찾을 수 없습니다.", null);
		}
		try (InputStream input = Files.newInputStream(modelPath)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[8192];
			for (int read; (read = input.read(buffer)) >= 0;) {
				digest.update(buffer, 0, read);
			}
			return java.util.HexFormat.of().formatHex(digest.digest());
		} catch (IOException exception) {
			throw new VisionRuntimeException(VisionRuntimeErrorCode.MODEL_LOAD_FAILED,
					"모델 파일을 읽을 수 없습니다.", exception);
		} catch (NoSuchAlgorithmException exception) {
			throw new VisionRuntimeException(VisionRuntimeErrorCode.RUNTIME_UNAVAILABLE,
					"SHA-256을 사용할 수 없습니다.", exception);
		}
	}
}
