package com.projecteden.village.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.projecteden.village.domain.VillageCategory;
import com.projecteden.village.domain.VillageTheme;

public record VillageInterpretationResponse(
		VillageTheme theme,
		VillageCategory primaryCategory,
		VillageCategory secondaryCategory,
		String message,
		List<VillageExpressionResponse> expressions,
		LocalDateTime appliedAt,
		String ruleVersion) {
}
