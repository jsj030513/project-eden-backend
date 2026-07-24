package com.projecteden.memorytaxonomy.evaluation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "eden.image-evaluation")
public class ImageEvaluationProperties {

	private boolean enabled = false;
	private String manifest = "";
	private String outputDirectory = "";
	private int maxCases = 100;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getManifest() {
		return manifest;
	}

	public void setManifest(String manifest) {
		this.manifest = manifest;
	}

	public String getOutputDirectory() {
		return outputDirectory;
	}

	public void setOutputDirectory(String outputDirectory) {
		this.outputDirectory = outputDirectory;
	}

	public int getMaxCases() {
		return maxCases;
	}

	public void setMaxCases(int maxCases) {
		this.maxCases = maxCases;
	}
}
