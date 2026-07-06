package com.projecteden.daily.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.character.domain.Character;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.daily.domain.DailyMission;
import com.projecteden.daily.dto.DailyMissionResponse;
import com.projecteden.daily.dto.PlantMissionCompleteResponse;
import com.projecteden.daily.repository.DailyMissionRepository;

@Service
public class DailyService {

	private final DailyMissionRepository dailyMissionRepository;
	private final CharacterRepository characterRepository;
	private final Clock clock;

	public DailyService(
			DailyMissionRepository dailyMissionRepository,
			CharacterRepository characterRepository,
			Clock clock) {
		this.dailyMissionRepository = dailyMissionRepository;
		this.characterRepository = characterRepository;
		this.clock = clock;
	}

	@Transactional
	public DailyMissionResponse getTodayMission(Long characterId) {
		return toResponse(findOrCreateTodayMission(characterId));
	}

	@Transactional
	public PlantMissionCompleteResponse completePlantMission(Long characterId) {
		DailyMission mission = findOrCreateTodayMission(characterId);
		mission.completePlantMission();
		return new PlantMissionCompleteResponse("씨앗 심기 미션을 완료했습니다.");
	}

	@Transactional
	public PlantMissionCompleteResponse completeHarvestMission(Long characterId) {
		DailyMission mission = findOrCreateTodayMission(characterId);
		mission.completeHarvestMission();
		return new PlantMissionCompleteResponse("수확 미션을 완료했습니다.");
	}

	@Transactional
	public DailyMissionResponse getMyTodayMission(Long userId) {
		Character character = characterRepository.findByUserId(userId)
				.orElseThrow(() -> new IllegalArgumentException("캐릭터를 찾을 수 없습니다."));
		return getTodayMission(character.getId());
	}

	private DailyMission findOrCreateTodayMission(Long characterId) {
		LocalDate today = LocalDate.now(clock);
		return dailyMissionRepository.findByCharacterIdAndMissionDate(characterId, today)
				.orElseGet(() -> {
					Character character = characterRepository.findById(characterId)
							.orElseThrow(() -> new IllegalArgumentException("캐릭터를 찾을 수 없습니다."));
					return dailyMissionRepository.save(DailyMission.create(character, today));
				});
	}

	private DailyMissionResponse toResponse(DailyMission mission) {
		return new DailyMissionResponse(
				mission.getMissionDate(),
				mission.isPlantCompleted(),
				mission.isHarvestCompleted(),
				mission.isPhotoCompleted(),
				mission.isRewardClaimed());
	}
}
