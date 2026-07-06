package com.projecteden.daily.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.character.domain.Character;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.daily.domain.DailyMission;
import com.projecteden.daily.dto.DailyMissionResponse;
import com.projecteden.daily.dto.DailyRewardResponse;
import com.projecteden.daily.dto.PlantMissionCompleteResponse;
import com.projecteden.daily.repository.DailyMissionRepository;
import com.projecteden.house.domain.House;
import com.projecteden.house.repository.HouseRepository;
import com.projecteden.inventory.domain.Inventory;
import com.projecteden.inventory.repository.InventoryRepository;
import com.projecteden.seed.domain.Seed;
import com.projecteden.seed.domain.SeedType;
import com.projecteden.seed.repository.SeedRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;

@Service
public class DailyService {
	private static final int DAILY_REWARD_GOLD = 50;
	private static final int DAILY_REWARD_SEED_QUANTITY = 2;

	private final DailyMissionRepository dailyMissionRepository;
	private final CharacterRepository characterRepository;
	private final WorldRepository worldRepository;
	private final HouseRepository houseRepository;
	private final InventoryRepository inventoryRepository;
	private final SeedRepository seedRepository;
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
			WorldRepository worldRepository,
			HouseRepository houseRepository,
			InventoryRepository inventoryRepository,
			SeedRepository seedRepository,
			Clock clock) {
		this.dailyMissionRepository = dailyMissionRepository;
		this.characterRepository = characterRepository;
		this.worldRepository = worldRepository;
		this.houseRepository = houseRepository;
		this.inventoryRepository = inventoryRepository;
		this.seedRepository = seedRepository;
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

	@Transactional
	public DailyRewardResponse claimMyDailyReward(Long userId) {
		Character character = characterRepository.findByUserId(userId)
				.orElseThrow(() -> new IllegalArgumentException("캐릭터를 찾을 수 없습니다."));
		return claimDailyReward(character.getId());
	}

	@Transactional
	public DailyRewardResponse claimDailyReward(Long characterId) {
		DailyMission mission = findOrCreateTodayMission(characterId);
		mission.claimReward();

		World world = worldRepository.findByCharacterId(characterId)
				.orElseThrow(() -> new IllegalArgumentException("월드를 찾을 수 없습니다."));
		House house = houseRepository.findByWorldId(world.getId())
				.orElseThrow(() -> new IllegalArgumentException("집을 찾을 수 없습니다."));
		Inventory inventory = inventoryRepository.findByHouseId(house.getId())
				.orElseThrow(() -> new IllegalArgumentException("인벤토리를 찾을 수 없습니다."));

		world.addGold(DAILY_REWARD_GOLD);
		Seed flowerSeed = seedRepository.findByInventoryIdAndSeedType(inventory.getId(), SeedType.FLOWER)
				.orElseGet(() -> Seed.create(inventory, SeedType.FLOWER, 0));
		flowerSeed.addQuantity(DAILY_REWARD_SEED_QUANTITY);
		seedRepository.save(flowerSeed);

		return new DailyRewardResponse(
				mission.getMissionDate(),
				DAILY_REWARD_GOLD,
				SeedType.FLOWER,
				DAILY_REWARD_SEED_QUANTITY,
				mission.isRewardClaimed(),
				"일일 미션 보상을 수령했습니다.");
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
