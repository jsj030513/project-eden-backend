package com.projecteden.penalty.dto; import com.projecteden.penalty.domain.PenaltyStage; public record DailyPenaltyResponse(int missedDays, PenaltyStage stage, String message) {}
