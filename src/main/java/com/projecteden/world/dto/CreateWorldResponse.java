package com.projecteden.world.dto;

import com.projecteden.world.domain.Season;
import com.projecteden.world.domain.Weather;

public record CreateWorldResponse(
		Long id,
		String worldName,
		Season season,
		Weather weather,
		int day,
		int gold,
		int wood,
		int stone,
		int food) {
}
