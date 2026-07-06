package com.projecteden.plant.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projecteden.plant.dto.PlantResponse;
import com.projecteden.plant.service.PlantService;
import com.projecteden.user.domain.User;

@RestController
@RequestMapping("/api/plants")
public class PlantController {

	private final PlantService plantService;

	public PlantController(PlantService plantService) {
		this.plantService = plantService;
	}

	@GetMapping("/me")
	public ResponseEntity<List<PlantResponse>> getMyPlants(@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(plantService.refreshMyPlantGrowth(user.getId()));
	}
}
