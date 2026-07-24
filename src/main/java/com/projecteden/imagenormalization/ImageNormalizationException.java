package com.projecteden.imagenormalization;

public class ImageNormalizationException extends RuntimeException {

	private final ImageNormalizationErrorCode errorCode;
	private final String safeMessage;

	public ImageNormalizationException(ImageNormalizationErrorCode errorCode, String safeMessage) {
		super(safeMessage);
		this.errorCode = errorCode;
		this.safeMessage = safeMessage;
	}

	public ImageNormalizationException(
			ImageNormalizationErrorCode errorCode,
			String safeMessage,
			Throwable cause) {
		super(safeMessage, cause);
		this.errorCode = errorCode;
		this.safeMessage = safeMessage;
	}

	public ImageNormalizationErrorCode getErrorCode() {
		return errorCode;
	}

	public String getSafeMessage() {
		return safeMessage;
	}
}
