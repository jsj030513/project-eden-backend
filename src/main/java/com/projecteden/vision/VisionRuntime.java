package com.projecteden.vision;

/** Internal runtime boundary; it is not an API or persistence contract. */
public interface VisionRuntime {
	VisionRuntimeHealth health();
}
