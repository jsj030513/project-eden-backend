package com.projecteden.vision.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VisionRankingMeta(
		@JsonProperty("model_id") String modelId,
		@JsonProperty("model_revision") String modelRevision,
		@JsonProperty("taxonomy_version") String taxonomyVersion,
		@JsonProperty("policy_version") String policyVersion,
		@JsonProperty("calibration_version") String calibrationVersion,
		String coverage) {}
