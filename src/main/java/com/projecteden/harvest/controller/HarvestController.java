package com.projecteden.harvest.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projecteden.harvest.dto.HarvestResponse;
import com.projecteden.harvest.service.HarvestService;
import com.projecteden.plant.dto.PlantResponse;
import com.projecteden.user.domain.User;

@RestController
@RequestMapping("/api/plants")
public class HarvestController {

	private final HarvestService harvestService;

	public HarvestController(HarvestService harvestService) {
		this.harvestService = harvestService;
	}

	@PostMapping("/{plantId}/harvest")
	public ResponseEntity<HarvestResponse> harvest(
			@AuthenticationPrincipal User user,
			@PathVariable Long plantId) {
		return ResponseEntity.ok(harvestService.harvest(user.getId(), plantId));
	}

	@GetMapping("/harvestable")
	public ResponseEntity<List<PlantResponse>> getHarvestablePlants(
			@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(harvestService.getHarvestablePlants(user.getId()));
	}
}
