package com.projecteden.seed.dto;

import com.projecteden.seed.domain.SeedType;

public record PlantSeedResponse(SeedType seedType, int remaining) {
}
