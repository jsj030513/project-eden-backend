package com.projecteden.npcmemory.controller;

import java.time.LocalDateTime;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.npc.repository.NpcRepository;
import com.projecteden.npcmemory.dto.NpcDialogueResponse;
import com.projecteden.npcmemory.service.NpcDialogueService;
import com.projecteden.user.domain.User;

@RestController
@RequestMapping("/api/npcs")
public class NpcMemoryController {

	private final CharacterRepository characters;
	private final NpcRepository npcs;
	private final NpcDialogueService dialogueService;

	public NpcMemoryController(
			CharacterRepository characters,
			NpcRepository npcs,
			NpcDialogueService dialogueService) {
		this.characters = characters;
		this.npcs = npcs;
		this.dialogueService = dialogueService;
	}

	@GetMapping("/{npcId}/dialogue")
	public ResponseEntity<NpcDialogueResponse> getDialogue(
			@AuthenticationPrincipal User user,
			@PathVariable Long npcId) {
		var character = characters.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("캐릭터를 찾을 수 없습니다."));
		npcs.findById(npcId)
				.orElseThrow(() -> new ResourceNotFoundException("NPC를 찾을 수 없습니다."));

		var result = dialogueService.getDialogue(character.getId(), npcId);
		return ResponseEntity.ok(NpcDialogueResponse.from(npcId, result, LocalDateTime.now()));
	}
}
