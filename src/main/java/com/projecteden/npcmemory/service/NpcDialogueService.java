package com.projecteden.npcmemory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.npcmemory.context.NpcContext;
import com.projecteden.npcmemory.context.NpcContextProvider;
import com.projecteden.npcmemory.dialogue.NpcDialogueResult;
import com.projecteden.npcmemory.dialogue.NpcDialogueRule;

@Service
public class NpcDialogueService {

	private final NpcContextProvider contextProvider;
	private final NpcDialogueRule dialogueRule;
	private final NpcMemoryService npcMemoryService;

	public NpcDialogueService(
			NpcContextProvider contextProvider,
			NpcDialogueRule dialogueRule,
			NpcMemoryService npcMemoryService) {
		this.contextProvider = contextProvider;
		this.dialogueRule = dialogueRule;
		this.npcMemoryService = npcMemoryService;
	}

	@Transactional
	public NpcDialogueResult getDialogue(Long characterId, Long npcId) {
		NpcContext context = contextProvider.buildContext(characterId, npcId);
		NpcDialogueResult result = dialogueRule.selectDialogue(context);
		npcMemoryService.recordInteraction(
				characterId,
				npcId,
				result.currentTheme(),
				result.rememberedCategory(),
				result.dialogueKey().name());
		return result.withMemoryChanged();
	}
}
