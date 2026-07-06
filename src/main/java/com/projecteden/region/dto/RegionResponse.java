package com.projecteden.region.dto;

import com.projecteden.region.domain.RegionType;

public record RegionResponse(
		Long id,
		RegionType regionType,
		String displayName,
		boolean unlocked) {
}
