package com.projecteden.resonance.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projecteden.resonance.dto.CreateResonanceRequest;
import com.projecteden.resonance.dto.ResonanceResponse;
import com.projecteden.resonance.service.ResonanceService;
import com.projecteden.user.domain.User;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/resonances")
public class ResonanceController {

	private final ResonanceService resonanceService;

	public ResonanceController(ResonanceService resonanceService) {
		this.resonanceService = resonanceService;
	}

	@PostMapping
	public ResponseEntity<ResonanceResponse> createResonance(
			@AuthenticationPrincipal User user,
			@Valid @RequestBody CreateResonanceRequest request) {
		return ResponseEntity.ok(
				resonanceService.createMyResonanceReward(user.getId(), request.recognitionId()));
	}

	@GetMapping("/me")
	public ResponseEntity<List<ResonanceResponse>> getMyResonances(
			@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(resonanceService.getMyResonances(user.getId()));
	}
}
