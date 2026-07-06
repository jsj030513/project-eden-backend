package com.projecteden.tutorial.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projecteden.tutorial.dto.AdvanceTutorialRequest;
import com.projecteden.tutorial.dto.TutorialResponse;
import com.projecteden.tutorial.service.TutorialService;
import com.projecteden.user.domain.User;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tutorial")
public class TutorialController {

	private final TutorialService tutorialService;

	public TutorialController(TutorialService tutorialService) {
		this.tutorialService = tutorialService;
	}

	@GetMapping("/me")
	public ResponseEntity<TutorialResponse> getMyTutorial(@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(tutorialService.getMyTutorial(user.getId()));
	}

	@PatchMapping("/advance")
	public ResponseEntity<TutorialResponse> advanceTutorial(
			@AuthenticationPrincipal User user,
			@Valid @RequestBody AdvanceTutorialRequest request) {
		return ResponseEntity.ok(tutorialService.advanceMyTutorial(user.getId(), request.nextStep()));
	}
}
