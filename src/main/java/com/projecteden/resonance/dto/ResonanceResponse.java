package com.projecteden.resonance.dto;

import java.time.LocalDate;

import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.resonance.domain.ResonanceRewardType;
import com.projecteden.seed.domain.SeedType;

public record ResonanceResponse(
		Long id,
		RecognizedObject recognizedObject,
		ResonanceRewardType rewardType,
		SeedType rewardSeedType,
		int rewardSeedQuantity,
		int rewardGold,
		LocalDate resonanceDate,
		String message) {
}
