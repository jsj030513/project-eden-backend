package com.projecteden.ranking.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.friend.domain.FriendStatus;
import com.projecteden.friend.repository.FriendRepository;
import com.projecteden.ranking.domain.Ranking;
import com.projecteden.ranking.dto.RankingResponseDTO;
import com.projecteden.ranking.repository.RankingRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;

@Service
public class RankingService {

	private static final Comparator<Ranking> RANKING_ORDER =
			Comparator.comparingInt(Ranking::getConsecutiveLogins).reversed()
					.thenComparing(Comparator.comparingInt(Ranking::getTotalPlayDays).reversed())
					.thenComparing(Comparator.comparingInt(Ranking::getSeasonAchievements).reversed());

	private final RankingRepository rankingRepository;
	private final FriendRepository friendRepository;
	private final UserRepository userRepository;

	public RankingService(
			RankingRepository rankingRepository,
			FriendRepository friendRepository,
			UserRepository userRepository) {
		this.rankingRepository = rankingRepository;
		this.friendRepository = friendRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public List<RankingResponseDTO> getFriendRanking(Long userId) {
		User me = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
		List<User> members = new ArrayList<>();
		members.add(me);
		friendRepository.findByRequesterOrReceiverAndStatus(me, me, FriendStatus.ACCEPTED)
				.forEach(friend -> members.add(
						friend.getRequester().getId().equals(me.getId())
								? friend.getReceiver()
								: friend.getRequester()));

		List<Long> memberIds = members.stream().map(User::getId).toList();
		Map<Long, Ranking> rankingByUserId = rankingRepository.findAllById(memberIds).stream()
				.collect(Collectors.toMap(Ranking::getUserId, Function.identity()));
		List<Ranking> missingRankings = members.stream()
				.filter(user -> !rankingByUserId.containsKey(user.getId()))
				.map(Ranking::create)
				.toList();
		rankingRepository.saveAll(missingRankings)
				.forEach(ranking -> rankingByUserId.put(ranking.getUserId(), ranking));

		return rankingByUserId.values().stream()
				.sorted(RANKING_ORDER)
				.map(this::toResponse)
				.toList();
	}

	private RankingResponseDTO toResponse(Ranking ranking) {
		return new RankingResponseDTO(
				ranking.getUserId(),
				ranking.getUser().getNickname(),
				ranking.getConsecutiveLogins(),
				ranking.getTotalPlayDays(),
				ranking.getSeasonAchievements());
	}
}
