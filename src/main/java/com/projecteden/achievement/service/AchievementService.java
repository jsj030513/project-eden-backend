package com.projecteden.achievement.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.achievement.domain.Achievement;
import com.projecteden.achievement.domain.UserAchievement;
import com.projecteden.achievement.dto.AchievementResponse;
import com.projecteden.achievement.repository.AchievementRepository;
import com.projecteden.achievement.repository.UserAchievementRepository;
import com.projecteden.character.domain.Character;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.collection.repository.CollectionRepository;
import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.evolution.domain.EvolutionSourceType;
import com.projecteden.evolution.service.EvolutionService;
import com.projecteden.statistics.service.StatisticsService;
import com.projecteden.title.service.TitleService;

@Service
public class AchievementService {

	private final AchievementRepository achievementRepository;
	private final UserAchievementRepository userAchievementRepository;
	private final CollectionRepository collectionRepository;
	private final CharacterRepository characterRepository;
	private final TitleService titleService;
	private final StatisticsService statisticsService;
	private final EvolutionService evolutionService;
	private final Clock clock;

	public AchievementService(
			AchievementRepository achievementRepository,
			UserAchievementRepository userAchievementRepository,
			CollectionRepository collectionRepository,
			CharacterRepository characterRepository,
			TitleService titleService,
			StatisticsService statisticsService,
			EvolutionService evolutionService,
			Clock clock) {
		this.achievementRepository = achievementRepository;
		this.userAchievementRepository = userAchievementRepository;
		this.collectionRepository = collectionRepository;
		this.characterRepository = characterRepository;
		this.titleService = titleService;
		this.statisticsService = statisticsService;
		this.evolutionService = evolutionService;
		this.clock = clock;
	}

	@Transactional
	public void evaluateAchievements(Long characterId) {
		Character character = findCharacter(characterId);
		long unique = collectionRepository.countByCharacterId(characterId);
		long total = collectionRepository.sumDiscoveredCountByCharacterId(characterId);
		int max = collectionRepository.maxDiscoveredCountByCharacterId(characterId);

		for (Achievement achievement : achievementRepository.findAll()) {
			if (!userAchievementRepository.existsByCharacterIdAndAchievementCode(
					characterId, achievement.getCode())
					&& qualifies(achievement, unique, total, max)) {
				userAchievementRepository.save(UserAchievement.create(
						character, achievement, LocalDateTime.now(clock)));
				evolutionService.addEvolutionPoint(characterId, EvolutionSourceType.ACHIEVEMENT);
				if (achievement.getRewardTitleCode() != null) {
					titleService.grantTitle(characterId, achievement.getRewardTitleCode());
				}
			}
		}
		statisticsService.refreshStatistics(characterId);
	}

	@Transactional(readOnly = true)
	public List<AchievementResponse> getMyAchievements(Long userId) {
		Character character = characterRepository.findByUserId(userId)
				.orElseThrow(() -> new ResourceNotFoundException("캐릭터를 찾을 수 없습니다."));
		Map<String, UserAchievement> achieved = userAchievementRepository
				.findByCharacterId(character.getId()).stream()
				.collect(Collectors.toMap(
						value -> value.getAchievement().getCode(), Function.identity()));
		return achievementRepository.findAll().stream()
				.map(achievement -> toResponse(achievement, achieved.get(achievement.getCode())))
				.toList();
	}

	private boolean qualifies(Achievement achievement, long unique, long total, int max) {
		return switch (achievement.getType()) {
			case FIRST_DISCOVERY -> total >= 1;
			case COLLECTION_COUNT -> unique >= achievement.getRequiredValue();
			case TOTAL_DISCOVERY_COUNT -> total >= achievement.getRequiredValue();
			case SAME_OBJECT_COUNT -> max >= achievement.getRequiredValue();
		};
	}

	private AchievementResponse toResponse(Achievement achievement, UserAchievement achieved) {
		return new AchievementResponse(
				achievement.getCode(), achievement.getName(), achievement.getDescription(),
				achievement.getType(), achievement.getRequiredValue(), achieved != null,
				achieved == null ? null : achieved.getAchievedAt(), achievement.getRewardTitleCode());
	}

	private Character findCharacter(Long id) {
		return characterRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("캐릭터를 찾을 수 없습니다."));
	}
}
