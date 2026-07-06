package com.projecteden.seed.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projecteden.seed.dto.PlantSeedRequest;
import com.projecteden.plant.dto.PlantSeedResultResponse;
import com.projecteden.seed.dto.SeedResponse;
import com.projecteden.seed.service.SeedService;
import com.projecteden.user.domain.User;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/seeds")
public class SeedController {

	private final SeedService seedService;

	public SeedController(SeedService seedService) {
		this.seedService = seedService;
	}

	@GetMapping("/me")
	public ResponseEntity<List<SeedResponse>> getMySeeds(@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(seedService.getMySeeds(user.getId()));
	}

	@PostMapping("/plant")
	public ResponseEntity<PlantSeedResultResponse> plantSeed(
			@AuthenticationPrincipal User user,
			@Valid @RequestBody PlantSeedRequest request) {
		return ResponseEntity.ok(seedService.plantMySeed(user.getId(), request.seedType()));
	}
}
