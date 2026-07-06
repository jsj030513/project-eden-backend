package com.projecteden.seed.dto;

import com.projecteden.seed.domain.SeedType;

public record SeedResponse(Long id, SeedType seedType, int quantity) {
}
