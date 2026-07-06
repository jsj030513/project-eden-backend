package com.projecteden.character.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projecteden.character.dto.CharacterResponse;
import com.projecteden.character.dto.CreateCharacterRequest;
import com.projecteden.character.service.CharacterService;
import com.projecteden.user.domain.User;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/characters")
public class CharacterController {

	private final CharacterService characterService;

	public CharacterController(CharacterService characterService) {
		this.characterService = characterService;
	}

	@PostMapping
	public ResponseEntity<CharacterResponse> createCharacter(
			@AuthenticationPrincipal User user,
			@Valid @RequestBody CreateCharacterRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(characterService.createCharacter(user.getId(), request));
	}

	@GetMapping("/me")
	public ResponseEntity<CharacterResponse> getMyCharacter(@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(characterService.getMyCharacter(user.getId()));
	}
}
