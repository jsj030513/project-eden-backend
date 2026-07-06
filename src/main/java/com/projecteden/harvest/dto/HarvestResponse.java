package com.projecteden.harvest.dto;

import com.projecteden.seed.domain.SeedType;

public record HarvestResponse(
		Long plantId,
		SeedType seedType,
		int earnedGold,
		SeedType earnedSeedType,
		int earnedSeedQuantity,
		String message) {
}
