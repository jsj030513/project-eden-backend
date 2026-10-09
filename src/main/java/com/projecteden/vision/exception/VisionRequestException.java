package com.projecteden.vision.exception;

public class VisionRequestException extends RuntimeException {
	public enum Reason { DISABLED, IMAGE_REQUIRED, IMAGE_EMPTY, IMAGE_TOO_LARGE, INVALID_REQUEST }

	private final Reason reason;

	public VisionRequestException(Reason reason) {
		super("Vision request rejected: " + reason);
		this.reason = reason;
	}

	public Reason getReason() { return reason; }
}
