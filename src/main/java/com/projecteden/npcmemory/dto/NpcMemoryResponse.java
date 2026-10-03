package com.projecteden.npcmemory.dto;

import java.time.LocalDateTime;

import com.projecteden.npcmemory.domain.NpcMemory;
import com.projecteden.village.domain.VillageCategory;
import com.projecteden.village.domain.VillageTheme;

public record NpcMemoryResponse(
		Long npcId,
		VillageTheme rememberedTheme,
		VillageCategory rememberedCategory,
		int interactionCount,
		String lastDialogueKey,
		LocalDateTime lastInteractedAt
) {

	public static NpcMemoryResponse from(NpcMemory memory) {
		return new NpcMemoryResponse(
				memory.getNpcId(),
				memory.getRememberedTheme(),
				memory.getRememberedCategory(),
				memory.getInteractionCount(),
				memory.getLastDialogueKey(),
				memory.getLastInteractedAt());
	}
}
