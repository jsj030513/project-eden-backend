package com.projecteden.plant.dto;

import java.time.LocalDateTime;

import com.projecteden.plant.domain.PlantStage;
import com.projecteden.region.domain.RegionType;
import com.projecteden.seed.domain.SeedType;

public record PlantResponse(
		Long id,
		SeedType seedType,
		PlantStage plantStage,
		boolean resonanceBoosted,
		RegionType regionType,
		LocalDateTime plantedAt) {
}
