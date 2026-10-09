package com.projecteden.vision.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/** Schema 1.1 ranking evidence; no accepted label or probability is implied. */
public record VisionClassifyResponse(
		@JsonProperty("request_id") String requestId,
		@JsonProperty("schema_version") String schemaVersion,
		String status,
		VisionImageInfo image,
		List<JsonNode> predictions,
		List<VisionRankingItem> ranking,
		@JsonProperty("unknown_decision") String unknownDecision,
		VisionRankingMeta meta) {
	public VisionClassifyResponse {
		if (predictions != null) predictions = List.copyOf(predictions);
		if (ranking != null) ranking = List.copyOf(ranking);
	}
}
