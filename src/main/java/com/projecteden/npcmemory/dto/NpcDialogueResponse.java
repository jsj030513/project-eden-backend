package com.projecteden.npcmemory.dto;

import java.time.LocalDateTime;

import com.projecteden.npcmemory.dialogue.NpcDialogueKey;
import com.projecteden.npcmemory.dialogue.NpcDialogueResult;
import com.projecteden.village.domain.VillageCategory;
import com.projecteden.village.domain.VillageTheme;

public record NpcDialogueResponse(
		Long npcId,
		NpcDialogueKey dialogueKey,
		String message,
		VillageTheme currentTheme,
		VillageCategory rememberedCategory,
		boolean memoryChanged,
		LocalDateTime spokenAt
) {

	public static NpcDialogueResponse from(
			Long npcId, NpcDialogueResult result, LocalDateTime spokenAt) {
		return new NpcDialogueResponse(
				npcId,
				result.dialogueKey(),
				result.message(),
				result.currentTheme(),
				result.rememberedCategory(),
				result.memoryChanged(),
				spokenAt);
	}
}
