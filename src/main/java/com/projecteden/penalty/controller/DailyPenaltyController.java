package com.projecteden.penalty.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projecteden.penalty.dto.DailyPenaltyResponse;
import com.projecteden.penalty.service.DailyPenaltyService;
import com.projecteden.user.domain.User;

@RestController
@RequestMapping("/api/penalties")
public class DailyPenaltyController {
	private final DailyPenaltyService dailyPenaltyService;
	public DailyPenaltyController(DailyPenaltyService dailyPenaltyService) { this.dailyPenaltyService = dailyPenaltyService; }
	@GetMapping("/me")
	public DailyPenaltyResponse get(@AuthenticationPrincipal User user) {
		return dailyPenaltyService.get(user.getId());
	}
}
