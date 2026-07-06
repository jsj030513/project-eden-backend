package com.projecteden.profile.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projecteden.profile.dto.ProfileResponseDTO;
import com.projecteden.profile.dto.ProfileUpdateDTO;
import com.projecteden.profile.service.ProfileService;
import com.projecteden.user.domain.User;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {
	private final ProfileService profileService;
	public ProfileController(ProfileService profileService) { this.profileService = profileService; }
	@GetMapping("/me")
	public ProfileResponseDTO get(@AuthenticationPrincipal User user) { return profileService.get(user.getId()); }
	@PutMapping("/me")
	public ProfileResponseDTO update(@AuthenticationPrincipal User user, @RequestBody ProfileUpdateDTO request) {
		return profileService.update(user.getId(), request);
	}
}
