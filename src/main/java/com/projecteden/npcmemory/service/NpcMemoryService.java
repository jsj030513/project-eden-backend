package com.projecteden.npcmemory.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.npcmemory.domain.NpcMemory;
import com.projecteden.npcmemory.repository.NpcMemoryRepository;
import com.projecteden.village.domain.VillageCategory;
import com.projecteden.village.domain.VillageTheme;

@Service
public class NpcMemoryService {

	private final NpcMemoryRepository npcMemories;

	public NpcMemoryService(NpcMemoryRepository npcMemories) {
		this.npcMemories = npcMemories;
	}

	@Transactional
	public NpcMemory getOrCreateMemory(Long characterId, Long npcId) {
		return npcMemories.findByCharacterIdAndNpcId(characterId, npcId)
				.orElseGet(() -> npcMemories.save(NpcMemory.create(characterId, npcId)));
	}

	@Transactional
	public NpcMemory recordInteraction(
			Long characterId,
			Long npcId,
			VillageTheme currentTheme,
			VillageCategory currentCategory,
			String dialogueKey) {
		NpcMemory memory = getOrCreateMemory(characterId, npcId);
		memory.recordInteraction(
				currentTheme == null ? VillageTheme.UNDEFINED : currentTheme,
				currentCategory,
				dialogueKey,
				LocalDateTime.now());
		return memory;
	}

	@Transactional(readOnly = true)
	public Optional<NpcMemory> getMemory(Long characterId, Long npcId) {
		return npcMemories.findByCharacterIdAndNpcId(characterId, npcId);
	}
}
