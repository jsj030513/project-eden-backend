package com.projecteden.plant.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.character.domain.Character;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.plant.domain.Plant;
import com.projecteden.plant.domain.PlantStage;
import com.projecteden.plant.dto.PlantResponse;
import com.projecteden.plant.repository.PlantRepository;
import com.projecteden.region.domain.Region;
import com.projecteden.region.domain.RegionType;
import com.projecteden.region.repository.RegionRepository;
import com.projecteden.seed.domain.SeedType;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;

@Service
public class PlantService {

	private final PlantRepository plantRepository;
	private final RegionRepository regionRepository;
	private final WorldRepository worldRepository;
	private final CharacterRepository characterRepository;
	private final Clock clock;

	public PlantService(
			PlantRepository plantRepository,
			RegionRepository regionRepository,
			WorldRepository worldRepository,
			CharacterRepository characterRepository,
			Clock clock) {
		this.plantRepository = plantRepository;
		this.regionRepository = regionRepository;
		this.worldRepository = worldRepository;
		this.characterRepository = characterRepository;
		this.clock = clock;
	}

	@Transactional
	public PlantResponse createPlantFromSeed(Long characterId, SeedType seedType) {
		Character character = characterRepository.findById(characterId)
				.orElseThrow(() -> new IllegalArgumentException("캐릭터를 찾을 수 없습니다."));
		World world = worldRepository.findByCharacterId(characterId)
				.orElseThrow(() -> new IllegalArgumentException("월드를 찾을 수 없습니다."));
		Region flowerField = regionRepository
				.findByWorldIdAndRegionType(world.getId(), RegionType.FLOWER_FIELD)
				.orElseThrow(() -> new IllegalArgumentException("꽃밭 지역을 찾을 수 없습니다."));

		boolean resonanceBoosted = plantRepository.findByCharacterId(characterId).isEmpty();
		Plant plant = plantRepository.save(Plant.create(
				flowerField,
				character,
				seedType,
				resonanceBoosted));

		// TODO: resonanceBoosted Plant는 성장 단계 전환 시 튜토리얼 빠른 성장 규칙을 적용한다.
		// TODO: 일반 Plant는 현실 시간 기반 성장 규칙을 적용한다.
		return toResponse(plant);
	}

	@Transactional(readOnly = true)
	public List<PlantResponse> getMyPlants(Long characterId) {
		return plantRepository.findByCharacterId(characterId).stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional
	public List<PlantResponse> refreshPlantGrowth(Long characterId) {
		LocalDateTime now = LocalDateTime.now(clock);
		return plantRepository.findByCharacterId(characterId).stream()
				.map(plant -> {
					PlantStage calculatedStage = calculateStage(plant, now);
					if (plant.getPlantStage() != calculatedStage) {
						plant.updateStage(calculatedStage);
					}
					plant.touchGrowthCheckedAt(now);
					return toResponse(plant);
				})
				.toList();
	}

	public PlantStage calculateStage(Plant plant, LocalDateTime now) {
		Duration elapsed = Duration.between(plant.getPlantedAt(), now);
		if (elapsed.isNegative()) {
			return PlantStage.SEED;
		}

		if (plant.isResonanceBoosted()) {
			if (elapsed.compareTo(Duration.ofSeconds(60)) >= 0) {
				return PlantStage.BLOOMED;
			}
			if (elapsed.compareTo(Duration.ofSeconds(30)) >= 0) {
				return PlantStage.GROWING;
			}
			if (elapsed.compareTo(Duration.ofSeconds(10)) >= 0) {
				return PlantStage.SPROUT;
			}
			return PlantStage.SEED;
		}

		if (elapsed.compareTo(Duration.ofDays(3)) >= 0) {
			return PlantStage.BLOOMED;
		}
		if (elapsed.compareTo(Duration.ofDays(2)) >= 0) {
			return PlantStage.GROWING;
		}
		if (elapsed.compareTo(Duration.ofDays(1)) >= 0) {
			return PlantStage.SPROUT;
		}
		return PlantStage.SEED;
	}

	@Transactional(readOnly = true)
	public List<PlantResponse> getMyPlantsByUserId(Long userId) {
		Character character = characterRepository.findByUserId(userId)
				.orElseThrow(() -> new IllegalArgumentException("캐릭터를 찾을 수 없습니다."));
		return getMyPlants(character.getId());
	}

	@Transactional
	public List<PlantResponse> refreshMyPlantGrowth(Long userId) {
		Character character = characterRepository.findByUserId(userId)
				.orElseThrow(() -> new IllegalArgumentException("캐릭터를 찾을 수 없습니다."));
		return refreshPlantGrowth(character.getId());
	}

	private PlantResponse toResponse(Plant plant) {
		return new PlantResponse(
				plant.getId(),
				plant.getSeedType(),
				plant.getPlantStage(),
				plant.isResonanceBoosted(),
				plant.getRegion().getRegionType(),
				plant.getPlantedAt());
	}
}
