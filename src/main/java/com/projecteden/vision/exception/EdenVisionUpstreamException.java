package com.projecteden.vision.exception;

/** Deliberately omits upstream bodies and underlying exception messages/causes. */
public class EdenVisionUpstreamException extends RuntimeException {
	public enum Kind { CONNECTION_FAILURE, TIMEOUT, HTTP_ERROR, MALFORMED_RESPONSE }

	private final Kind kind;
	private final Integer statusCode;

	public EdenVisionUpstreamException(Kind kind) { this(kind, null); }

	public EdenVisionUpstreamException(Kind kind, Integer statusCode) {
		super("Eden Vision upstream failure: " + kind);
		this.kind = kind;
		this.statusCode = statusCode;
	}

	public Kind getKind() { return kind; }
	public Integer getStatusCode() { return statusCode; }
}
