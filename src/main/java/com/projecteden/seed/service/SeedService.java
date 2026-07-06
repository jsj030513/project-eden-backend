package com.projecteden.seed.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.character.domain.Character;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.house.domain.House;
import com.projecteden.house.repository.HouseRepository;
import com.projecteden.inventory.domain.Inventory;
import com.projecteden.inventory.repository.InventoryRepository;
import com.projecteden.plant.dto.PlantResponse;
import com.projecteden.plant.dto.PlantSeedResultResponse;
import com.projecteden.plant.service.PlantService;
import com.projecteden.seed.domain.Seed;
import com.projecteden.seed.domain.SeedType;
import com.projecteden.seed.dto.SeedResponse;
import com.projecteden.seed.repository.SeedRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;

@Service
public class SeedService {

	private final SeedRepository seedRepository;
	private final InventoryRepository inventoryRepository;
	private final HouseRepository houseRepository;
	private final WorldRepository worldRepository;
	private final CharacterRepository characterRepository;
	private final PlantService plantService;

	public SeedService(
			SeedRepository seedRepository,
			InventoryRepository inventoryRepository,
			HouseRepository houseRepository,
			WorldRepository worldRepository,
			CharacterRepository characterRepository,
			PlantService plantService) {
		this.seedRepository = seedRepository;
		this.inventoryRepository = inventoryRepository;
		this.houseRepository = houseRepository;
		this.worldRepository = worldRepository;
		this.characterRepository = characterRepository;
		this.plantService = plantService;
	}

	@Transactional
	public SeedResponse createStarterSeeds(Long characterId) {
		Inventory inventory = findInventoryByCharacterId(characterId);
		Seed seed = Seed.create(inventory, SeedType.FLOWER, 5);
		return toResponse(seedRepository.save(seed));
	}

	@Transactional
	public PlantSeedResultResponse plantSeed(Long characterId, SeedType seedType) {
		Inventory inventory = findInventoryByCharacterId(characterId);
		Seed seed = seedRepository.findByInventoryIdAndSeedType(inventory.getId(), seedType)
				.orElseThrow(() -> new IllegalArgumentException("씨앗을 찾을 수 없습니다."));

		int remaining = seed.useOne();
		PlantResponse plant = plantService.createPlantFromSeed(characterId, seedType);
		return new PlantSeedResultResponse(
				seedType,
				remaining,
				plant.id(),
				plant.plantStage(),
				plant.resonanceBoosted());
	}

	@Transactional(readOnly = true)
	public List<SeedResponse> getMySeeds(Long userId) {
		Character character = findCharacterByUserId(userId);
		Inventory inventory = findInventoryByCharacterId(character.getId());
		return seedRepository.findByInventoryId(inventory.getId()).stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional
	public PlantSeedResultResponse plantMySeed(Long userId, SeedType seedType) {
		Character character = findCharacterByUserId(userId);
		return plantSeed(character.getId(), seedType);
	}

	private Character findCharacterByUserId(Long userId) {
		return characterRepository.findByUserId(userId)
				.orElseThrow(() -> new IllegalArgumentException("캐릭터를 찾을 수 없습니다."));
	}

	private Inventory findInventoryByCharacterId(Long characterId) {
		World world = worldRepository.findByCharacterId(characterId)
				.orElseThrow(() -> new IllegalArgumentException("월드를 찾을 수 없습니다."));
		House house = houseRepository.findByWorldId(world.getId())
				.orElseThrow(() -> new IllegalArgumentException("집을 찾을 수 없습니다."));
		return inventoryRepository.findByHouseId(house.getId())
				.orElseThrow(() -> new IllegalArgumentException("인벤토리를 찾을 수 없습니다."));
	}

	private SeedResponse toResponse(Seed seed) {
		return new SeedResponse(seed.getId(), seed.getSeedType(), seed.getQuantity());
	}
}
