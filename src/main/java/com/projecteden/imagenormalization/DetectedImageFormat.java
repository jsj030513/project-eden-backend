package com.projecteden.imagenormalization;

public record DetectedImageFormat(
		ImageFormat format,
		String declaredMime,
		String inferredMime,
		String extension,
		boolean signatureMatched,
		boolean mimeMatched,
		boolean extensionMatched) {
}
