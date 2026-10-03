package com.projecteden.npcmemory.context;

import java.time.LocalDateTime;

import com.projecteden.village.domain.VillageCategory;
import com.projecteden.village.domain.VillageHistoryType;
import com.projecteden.village.domain.VillageTheme;

public record NpcContext(
		Long characterId,
		Long npcId,
		VillageTheme currentTheme,
		VillageCategory primaryCategory,
		VillageCategory secondaryCategory,
		VillageHistoryType recentHistoryType,
		VillageCategory recentHistoryCategory,
		VillageCategory rememberedCategory,
		int interactionCount,
		String lastDialogueKey,
		boolean firstMeeting,
		LocalDateTime lastInteractedAt
) {
}
