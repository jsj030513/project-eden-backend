package com.projecteden.vision.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "eden.vision")
public class VisionModelProperties {

	private boolean enabled;
	private String runtime = "onnx";
	private final Model model = new Model();
	private final YoloX yolox = new YoloX();
	private final Rules rules = new Rules();
	private final Taxonomy taxonomy = new Taxonomy();

	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
	public String getRuntime() { return runtime; }
	public void setRuntime(String runtime) { this.runtime = runtime; }
	public Model getModel() { return model; }
	public YoloX getYolox() { return yolox; }
	public Rules getRules() { return rules; }
	public Taxonomy getTaxonomy() { return taxonomy; }

	public static class Model {
		private String type = "yolox-nano";
		private String path = "";
		private String sha256 = "";
		public String getType() { return type; }
		public void setType(String type) { this.type = type; }
		public String getPath() { return path; }
		public void setPath(String path) { this.path = path; }
		public String getSha256() { return sha256; }
		public void setSha256(String sha256) { this.sha256 = sha256; }
	}

	public static class YoloX {
		// YOLOX-Nano's 0.30 proof threshold discarded valid normalized phone photos.
		// Keep the production floor conservative enough to reject the 0.10-range
		// false positives observed in the local runtime.
		private float confidenceThreshold = 0.20f;
		private float nmsThreshold = 0.45f;
		private int maxDetections = 100;
		public float getConfidenceThreshold() { return confidenceThreshold; }
		public void setConfidenceThreshold(float confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }
		public float getNmsThreshold() { return nmsThreshold; }
		public void setNmsThreshold(float nmsThreshold) { this.nmsThreshold = nmsThreshold; }
		public int getMaxDetections() { return maxDetections; }
		public void setMaxDetections(int maxDetections) { this.maxDetections = maxDetections; }
	}

	public static class Rules {
		private float activityThreshold = .65f;
		private float relationshipThreshold = .55f;
		private float proximityThreshold = .25f;
		public float getActivityThreshold() { return activityThreshold; }
		public void setActivityThreshold(float activityThreshold) { this.activityThreshold = activityThreshold; }
		public float getRelationshipThreshold() { return relationshipThreshold; }
		public void setRelationshipThreshold(float relationshipThreshold) { this.relationshipThreshold = relationshipThreshold; }
		public float getProximityThreshold() { return proximityThreshold; }
		public void setProximityThreshold(float proximityThreshold) { this.proximityThreshold = proximityThreshold; }
	}
	public static class Taxonomy {
		private float objectThreshold = .50f;
		private float activityThreshold = .70f;
		private float relationshipThreshold = .75f;
		public float getObjectThreshold() { return objectThreshold; }
		public void setObjectThreshold(float value) { objectThreshold = value; }
		public float getActivityThreshold() { return activityThreshold; }
		public void setActivityThreshold(float value) { activityThreshold = value; }
		public float getRelationshipThreshold() { return relationshipThreshold; }
		public void setRelationshipThreshold(float value) { relationshipThreshold = value; }
	}
}
