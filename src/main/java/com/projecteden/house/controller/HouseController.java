package com.projecteden.house.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projecteden.house.dto.CreateHouseResponse;
import com.projecteden.house.service.HouseService;
import com.projecteden.user.domain.User;

@RestController
@RequestMapping("/api/houses")
public class HouseController {

	private final HouseService houseService;

	public HouseController(HouseService houseService) {
		this.houseService = houseService;
	}

	@PostMapping
	public ResponseEntity<CreateHouseResponse> createHouse(@AuthenticationPrincipal User user) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(houseService.createMyHouse(user.getId()));
	}

	@GetMapping("/me")
	public ResponseEntity<CreateHouseResponse> getMyHouse(@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(houseService.getMyHouse(user.getId()));
	}
}
