package com.projecteden.vision;

public class VisionRuntimeException extends RuntimeException {

	private final VisionRuntimeErrorCode errorCode;

	public VisionRuntimeException(VisionRuntimeErrorCode errorCode, String safeMessage, Throwable cause) {
		super(safeMessage, cause);
		this.errorCode = errorCode;
	}

	public VisionRuntimeErrorCode getErrorCode() {
		return errorCode;
	}
}
