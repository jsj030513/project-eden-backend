package com.projecteden.daily.dto;

import java.time.LocalDate;

public record DailyMissionResponse(
		LocalDate missionDate,
		boolean plantCompleted,
		boolean harvestCompleted,
		boolean photoCompleted,
		boolean rewardClaimed) {
}
