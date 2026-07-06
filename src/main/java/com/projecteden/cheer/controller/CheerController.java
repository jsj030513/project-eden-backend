package com.projecteden.cheer.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projecteden.cheer.dto.CheerResponseDTO;
import com.projecteden.cheer.service.CheerService;
import com.projecteden.user.domain.User;

@RestController
@RequestMapping("/api/cheers")
public class CheerController {
	private final CheerService cheerService;
	public CheerController(CheerService cheerService) { this.cheerService = cheerService; }
	@PostMapping("/{friendId}")
	public CheerResponseDTO cheer(@AuthenticationPrincipal User user, @PathVariable Long friendId) {
		return cheerService.cheer(user.getId(), friendId);
	}
}
