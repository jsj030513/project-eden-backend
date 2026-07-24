package com.projecteden.vision;

public record VisionRuntimeHealth(
		boolean available,
		String runtimeVersion,
		String osName,
		String osArchitecture,
		String javaVersion,
		VisionRuntimeErrorCode errorCode) {
}
