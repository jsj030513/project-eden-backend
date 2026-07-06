package com.projecteden.world.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projecteden.user.domain.User;
import com.projecteden.world.dto.CreateWorldResponse;
import com.projecteden.world.service.WorldService;

@RestController
@RequestMapping("/api/worlds")
public class WorldController {

	private final WorldService worldService;

	public WorldController(WorldService worldService) {
		this.worldService = worldService;
	}

	@PostMapping
	public ResponseEntity<CreateWorldResponse> createWorld(@AuthenticationPrincipal User user) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(worldService.createMyWorld(user.getId()));
	}

	@GetMapping("/me")
	public ResponseEntity<CreateWorldResponse> getMyWorld(@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(worldService.getMyWorld(user.getId()));
	}
}
