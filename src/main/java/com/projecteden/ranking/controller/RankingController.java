package com.projecteden.ranking.controller;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.projecteden.ranking.dto.RankingResponseDTO;
import com.projecteden.ranking.service.RankingService;
import com.projecteden.user.domain.User;

@RestController
@RequestMapping("/api/ranking")
public class RankingController {
	private final RankingService rankingService;
	public RankingController(RankingService rankingService) { this.rankingService = rankingService; }
	@GetMapping("/friends")
	public List<RankingResponseDTO> ranking(@AuthenticationPrincipal User user) {
		return rankingService.getFriendRanking(user.getId());
	}
}
