package com.projecteden.penalty.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.penalty.domain.DailyPenalty;
import com.projecteden.penalty.domain.PenaltyStage;
import com.projecteden.penalty.dto.DailyPenaltyResponse;
import com.projecteden.penalty.repository.DailyPenaltyRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;

@Service
public class DailyPenaltyService {

	private final DailyPenaltyRepository penaltyRepository;
	private final UserRepository userRepository;
	private final Clock clock;

	public DailyPenaltyService(
			DailyPenaltyRepository penaltyRepository,
			UserRepository userRepository,
			Clock clock) {
		this.penaltyRepository = penaltyRepository;
		this.userRepository = userRepository;
		this.clock = clock;
	}

	@Transactional
	public DailyPenaltyResponse get(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
		DailyPenalty penalty = penaltyRepository.findByUser(user)
				.orElseGet(() -> penaltyRepository.save(DailyPenalty.create(user)));
		LocalDate today = LocalDate.now(clock);
		LocalDate lastLoginDate = user.getLastLoginAt() == null
				? today
				: user.getLastLoginAt().toLocalDate();
		int missedDays = (int) Math.max(0, ChronoUnit.DAYS.between(lastLoginDate, today));
		penalty.updateMissedDays(missedDays);
		return new DailyPenaltyResponse(
				missedDays,
				stageFor(missedDays),
				"게임 데이터는 유지되며 섬의 시각적 상태만 변경됩니다.");
	}

	private PenaltyStage stageFor(int missedDays) {
		return switch (missedDays) {
			case 0 -> PenaltyStage.NONE;
			case 1 -> PenaltyStage.WEEDS;
			case 2 -> PenaltyStage.LEAVES;
			case 3 -> PenaltyStage.WILTED_FLOWERS;
			default -> PenaltyStage.DESOLATE_ISLAND;
		};
	}
}
