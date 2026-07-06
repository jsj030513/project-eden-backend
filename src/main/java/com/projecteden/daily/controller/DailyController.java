package com.projecteden.daily.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.projecteden.daily.dto.DailyMissionResponse;
import com.projecteden.daily.dto.DailyRewardResponse;
import com.projecteden.daily.service.DailyService;
import com.projecteden.user.domain.User;

@RestController
@RequestMapping("/api/daily")
public class DailyController {

	private final DailyService dailyService;

	public DailyController(DailyService dailyService) {
		this.dailyService = dailyService;
	}

	@GetMapping
	public ResponseEntity<DailyMissionResponse> getTodayMission(@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(dailyService.getMyTodayMission(user.getId()));
	}

	@PostMapping("/reward")
	public ResponseEntity<DailyRewardResponse> claimDailyReward(@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(dailyService.claimMyDailyReward(user.getId()));
	}
}
