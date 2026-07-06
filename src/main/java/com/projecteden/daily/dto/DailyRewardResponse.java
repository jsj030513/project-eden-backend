package com.projecteden.daily.dto;

import java.time.LocalDate;

import com.projecteden.seed.domain.SeedType;

public record DailyRewardResponse(
		LocalDate missionDate,
		int earnedGold,
		SeedType earnedSeedType,
		int earnedSeedQuantity,
		boolean rewardClaimed,
		String message) {
}
