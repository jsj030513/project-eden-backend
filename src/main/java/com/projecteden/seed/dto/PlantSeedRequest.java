package com.projecteden.seed.dto;

import com.projecteden.seed.domain.SeedType;

import jakarta.validation.constraints.NotNull;

public record PlantSeedRequest(@NotNull SeedType seedType) {
}
