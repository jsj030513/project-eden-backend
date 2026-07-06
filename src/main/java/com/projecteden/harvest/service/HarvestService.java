package com.projecteden.harvest.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.character.domain.Character;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.daily.service.DailyService;
import com.projecteden.harvest.dto.HarvestResponse;
import com.projecteden.house.domain.House;
import com.projecteden.house.repository.HouseRepository;
import com.projecteden.inventory.domain.Inventory;
import com.projecteden.inventory.repository.InventoryRepository;
import com.projecteden.plant.domain.Plant;
import com.projecteden.plant.domain.PlantStage;
import com.projecteden.plant.dto.PlantResponse;
import com.projecteden.plant.repository.PlantRepository;
import com.projecteden.plant.service.PlantService;
import com.projecteden.seed.domain.Seed;
import com.projecteden.seed.domain.SeedType;
import com.projecteden.seed.repository.SeedRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;

@Service
public class HarvestService {

	private final PlantRepository plantRepository;
	private final PlantService plantService;
	private final SeedRepository seedRepository;
	private final InventoryRepository inventoryRepository;
	private final HouseRepository houseRepository;
	private final WorldRepository worldRepository;
	private final CharacterRepository characterRepository;
	private final DailyService dailyService;

	public HarvestService(
			PlantRepository plantRepository,
			PlantService plantService,
			SeedRepository seedRepository,
			InventoryRepository inventoryRepository,
			HouseRepository houseRepository,
			WorldRepository worldRepository,
			CharacterRepository characterRepository,
			DailyService dailyService) {
		this.plantRepository = plantRepository;
		this.plantService = plantService;
		this.seedRepository = seedRepository;
		this.inventoryRepository = inventoryRepository;
		this.houseRepository = houseRepository;
		this.worldRepository = worldRepository;
		this.characterRepository = characterRepository;
		this.dailyService = dailyService;
	}

	@Transactional
	public HarvestResponse harvest(Long userId, Long plantId) {
		Plant plant = plantRepository.findById(plantId)
				.orElseThrow(() -> new ResourceNotFoundException("식물을 찾을 수 없습니다."));
		if (!plant.getCharacter().getUser().getId().equals(userId)) {
			throw new IllegalArgumentException("다른 사용자의 식물은 수확할 수 없습니다.");
		}
		if (plant.getPlantStage() != PlantStage.BLOOMED) {
			throw new IllegalArgumentException("개화한 식물만 수확할 수 있습니다.");
		}

		SeedType seedType = plant.getSeedType();
		int earnedGold = earnedGold(seedType);
		World world = findWorld(plant.getCharacter().getId());
		Inventory inventory = findInventory(world);

		world.addGold(earnedGold);
		Seed rewardSeed = seedRepository.findByInventoryIdAndSeedType(inventory.getId(), seedType)
				.orElseGet(() -> seedRepository.save(Seed.create(inventory, seedType, 0)));
		rewardSeed.addQuantity(1);
		plantRepository.delete(plant);
		dailyService.completeHarvestMission(plant.getCharacter().getId());

		return new HarvestResponse(
				plantId,
				seedType,
				earnedGold,
				seedType,
				1,
				seedType + "를 수확했습니다.");
	}

	@Transactional
	public List<PlantResponse> getHarvestablePlants(Long userId) {
		Character character = characterRepository.findByUserId(userId)
				.orElseThrow(() -> new IllegalArgumentException("캐릭터를 찾을 수 없습니다."));
		return plantService.refreshPlantGrowth(character.getId()).stream()
				.filter(plant -> plant.plantStage() == PlantStage.BLOOMED)
				.toList();
	}

	private World findWorld(Long characterId) {
		return worldRepository.findByCharacterId(characterId)
				.orElseThrow(() -> new IllegalArgumentException("월드를 찾을 수 없습니다."));
	}

	private Inventory findInventory(World world) {
		House house = houseRepository.findByWorldId(world.getId())
				.orElseThrow(() -> new IllegalArgumentException("집을 찾을 수 없습니다."));
		return inventoryRepository.findByHouseId(house.getId())
				.orElseThrow(() -> new IllegalArgumentException("인벤토리를 찾을 수 없습니다."));
	}

	private int earnedGold(SeedType seedType) {
		return switch (seedType) {
			case FLOWER -> 10;
			case WHEAT -> 15;
			case CARROT, POTATO -> 20;
			case TOMATO -> 25;
		};
	}
}
