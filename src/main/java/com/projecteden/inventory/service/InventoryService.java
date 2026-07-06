package com.projecteden.inventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.character.domain.Character;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.house.domain.House;
import com.projecteden.house.repository.HouseRepository;
import com.projecteden.inventory.domain.Inventory;
import com.projecteden.inventory.dto.CreateInventoryResponse;
import com.projecteden.inventory.repository.InventoryRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;

@Service
public class InventoryService {

	private final InventoryRepository inventoryRepository;
	private final HouseRepository houseRepository;
	private final WorldRepository worldRepository;
	private final CharacterRepository characterRepository;

	public InventoryService(
			InventoryRepository inventoryRepository,
			HouseRepository houseRepository,
			WorldRepository worldRepository,
			CharacterRepository characterRepository) {
		this.inventoryRepository = inventoryRepository;
		this.houseRepository = houseRepository;
		this.worldRepository = worldRepository;
		this.characterRepository = characterRepository;
	}

	@Transactional
	public CreateInventoryResponse createMyInventory(Long userId) {
		House house = findHouseByUserId(userId);
		return createInventory(house.getId());
	}

	@Transactional
	public CreateInventoryResponse createInventory(Long houseId) {
		House house = houseRepository.findById(houseId)
				.orElseThrow(() -> new IllegalArgumentException("집을 찾을 수 없습니다."));

		if (inventoryRepository.existsByHouseId(houseId)) {
			throw new IllegalArgumentException("이미 인벤토리가 존재합니다.");
		}

		Inventory inventory = inventoryRepository.save(Inventory.create(house));

		// TODO: 향후 Item, Photo, Furniture, Seed, Pet, Craft 시스템과 연결한다.
		return toResponse(inventory);
	}

	@Transactional(readOnly = true)
	public CreateInventoryResponse getMyInventory(Long userId) {
		House house = findHouseByUserId(userId);
		Inventory inventory = inventoryRepository.findByHouseId(house.getId())
				.orElseThrow(() -> new ResourceNotFoundException("인벤토리를 찾을 수 없습니다."));
		return toResponse(inventory);
	}

	private House findHouseByUserId(Long userId) {
		Character character = characterRepository.findByUserId(userId)
				.orElseThrow(() -> new IllegalArgumentException("캐릭터를 찾을 수 없습니다."));
		World world = worldRepository.findByCharacterId(character.getId())
				.orElseThrow(() -> new IllegalArgumentException("월드를 찾을 수 없습니다."));
		return houseRepository.findByWorldId(world.getId())
				.orElseThrow(() -> new IllegalArgumentException("집을 찾을 수 없습니다."));
	}

	private CreateInventoryResponse toResponse(Inventory inventory) {
		return new CreateInventoryResponse(
				inventory.getId(),
				inventory.getCapacity(),
				inventory.getUsedSlot());
	}
}
