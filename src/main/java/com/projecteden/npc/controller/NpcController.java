package com.projecteden.npc.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projecteden.npc.dto.NpcResponse;
import com.projecteden.npc.service.NpcService;
import com.projecteden.user.domain.User;

@RestController
@RequestMapping("/api/npcs")
public class NpcController {

	private final NpcService npcService;

	public NpcController(NpcService npcService) {
		this.npcService = npcService;
	}

	@GetMapping("/me")
	public ResponseEntity<List<NpcResponse>> getMyNpcs(@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(npcService.getMyNpcs(user.getId()));
	}
}
