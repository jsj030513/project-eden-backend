package com.projecteden.vision.runtime;

/** Cached readiness outcome for the opt-in local vision runtime. */
public enum LocalVisionRuntimeStatus {
	DISABLED,
	READY,
	MODEL_MISSING,
	CHECKSUM_MISMATCH,
	MODEL_LOAD_FAILED,
	RUNTIME_UNAVAILABLE
}
