package com.projecteden.vision.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record VisionRankingItem(
		Integer rank,
		@JsonProperty("concept_id") String conceptId,
		@JsonProperty("display_name") String displayName,
		@JsonProperty("broad_category") String broadCategory,
		List<String> hierarchy,
		@JsonProperty("raw_score") Double rawScore,
		@JsonProperty("score_kind") String scoreKind) {
	public VisionRankingItem {
		if (hierarchy != null) hierarchy = List.copyOf(hierarchy);
	}
}
