package com.projecteden.house.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.character.domain.Character;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.house.domain.House;
import com.projecteden.house.dto.CreateHouseResponse;
import com.projecteden.house.repository.HouseRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;

@Service
public class HouseService {

	private final HouseRepository houseRepository;
	private final WorldRepository worldRepository;
	private final CharacterRepository characterRepository;

	public HouseService(
			HouseRepository houseRepository,
			WorldRepository worldRepository,
			CharacterRepository characterRepository) {
		this.houseRepository = houseRepository;
		this.worldRepository = worldRepository;
		this.characterRepository = characterRepository;
	}

	@Transactional
	public CreateHouseResponse createMyHouse(Long userId) {
		World world = findWorldByUserId(userId);
		return createHouse(world.getId());
	}

	@Transactional
	public CreateHouseResponse createHouse(Long worldId) {
		World world = worldRepository.findById(worldId)
				.orElseThrow(() -> new IllegalArgumentException("월드를 찾을 수 없습니다."));

		if (houseRepository.existsByWorldId(worldId)) {
			throw new IllegalArgumentException("이미 집이 존재합니다.");
		}

		House house = houseRepository.save(House.create(world));

		// TODO: 향후 Furniture, Pet, NPC, Photo, Friend Visit 시스템과 연결한다.
		return toResponse(house);
	}

	@Transactional(readOnly = true)
	public CreateHouseResponse getMyHouse(Long userId) {
		World world = findWorldByUserId(userId);
		House house = houseRepository.findByWorldId(world.getId())
				.orElseThrow(() -> new ResourceNotFoundException("집을 찾을 수 없습니다."));
		return toResponse(house);
	}

	private World findWorldByUserId(Long userId) {
		Character character = characterRepository.findByUserId(userId)
				.orElseThrow(() -> new IllegalArgumentException("캐릭터를 찾을 수 없습니다."));
		return worldRepository.findByCharacterId(character.getId())
				.orElseThrow(() -> new IllegalArgumentException("월드를 찾을 수 없습니다."));
	}

	private CreateHouseResponse toResponse(House house) {
		return new CreateHouseResponse(
				house.getId(),
				house.getHouseName(),
				house.getLevel(),
				house.getHouseType(),
				house.getMaxDecoration());
	}
}
