package com.projecteden.plant.dto;

import com.projecteden.plant.domain.PlantStage;
import com.projecteden.seed.domain.SeedType;

public record PlantSeedResultResponse(
		SeedType seedType,
		int remaining,
		Long plantId,
		PlantStage plantStage,
		boolean resonanceBoosted) {
}
