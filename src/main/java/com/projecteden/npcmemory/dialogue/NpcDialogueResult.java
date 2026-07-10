package com.projecteden.npcmemory.dialogue;

import com.projecteden.village.domain.VillageCategory;
import com.projecteden.village.domain.VillageTheme;

public record NpcDialogueResult(
		NpcDialogueKey dialogueKey,
		String message,
		VillageTheme currentTheme,
		VillageCategory rememberedCategory,
		boolean memoryChanged
) {

	public NpcDialogueResult withMemoryChanged() {
		return new NpcDialogueResult(
				dialogueKey,
				message,
				currentTheme,
				rememberedCategory,
				true);
	}
}
