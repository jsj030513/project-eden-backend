package com.projecteden.vision.runtime;

import ai.onnxruntime.OrtEnvironment;
import org.springframework.stereotype.Component;

import com.projecteden.vision.VisionRuntime;
import com.projecteden.vision.VisionRuntimeErrorCode;
import com.projecteden.vision.VisionRuntimeHealth;

@Component
public class OnnxVisionRuntime implements VisionRuntime {

	@Override
	public VisionRuntimeHealth health() {
		try {
			OrtEnvironment environment = OrtEnvironment.getEnvironment();
			return new VisionRuntimeHealth(true, environment.getVersion(), osName(), osArchitecture(), javaVersion(), null);
		} catch (Throwable failure) {
			return new VisionRuntimeHealth(false, null, osName(), osArchitecture(), javaVersion(),
					failure instanceof UnsatisfiedLinkError
							? VisionRuntimeErrorCode.NATIVE_LIBRARY_LOAD_FAILED
							: VisionRuntimeErrorCode.RUNTIME_UNAVAILABLE);
		}
	}

	private String osName() { return System.getProperty("os.name", "unknown"); }
	private String osArchitecture() { return System.getProperty("os.arch", "unknown"); }
	private String javaVersion() { return System.getProperty("java.version", "unknown"); }
}
