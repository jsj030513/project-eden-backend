package com.projecteden.region.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projecteden.region.dto.RegionResponse;
import com.projecteden.region.service.RegionService;
import com.projecteden.user.domain.User;

@RestController
@RequestMapping("/api/regions")
public class RegionController {

	private final RegionService regionService;

	public RegionController(RegionService regionService) {
		this.regionService = regionService;
	}

	@GetMapping("/me")
	public ResponseEntity<List<RegionResponse>> getMyRegions(@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(regionService.getMyRegions(user.getId()));
	}
}
