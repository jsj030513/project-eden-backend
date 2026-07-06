package com.projecteden.world.service;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.character.domain.Character;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.npc.service.NpcService;
import com.projecteden.region.service.RegionService;
import com.projecteden.world.domain.World;
import com.projecteden.world.dto.CreateWorldResponse;
import com.projecteden.world.repository.WorldRepository;

@Service
public class WorldService {

	private final WorldRepository worldRepository;
	private final CharacterRepository characterRepository;
	private final RegionService regionService;
	private final NpcService npcService;

	public WorldService(
			WorldRepository worldRepository,
			CharacterRepository characterRepository,
			RegionService regionService,
			NpcService npcService) {
		this.worldRepository = worldRepository;
		this.characterRepository = characterRepository;
		this.regionService = regionService;
		this.npcService = npcService;
	}

	@Transactional
	public CreateWorldResponse createMyWorld(Long userId) {
		Character character = findCharacterByUserId(userId);
		return createWorld(character.getId());
	}

	@Transactional
	public CreateWorldResponse createWorld(Long characterId) {
		Character character = characterRepository.findById(characterId)
				.orElseThrow(() -> new IllegalArgumentException("캐릭터를 찾을 수 없습니다."));

		if (worldRepository.existsByCharacterId(characterId)) {
			throw new IllegalArgumentException("이미 월드가 존재합니다.");
		}

		long seed = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
		World world = worldRepository.save(World.create(character, seed));
		regionService.createDefaultRegions(world.getId());
		npcService.createDefaultNpcs(world.getId());

		// TODO: 향후 House, NPC, Inventory, Seed 시스템 생성 및 초기화와 연결한다.
		return toResponse(world);
	}

	@Transactional(readOnly = true)
	public CreateWorldResponse getMyWorld(Long userId) {
		Character character = findCharacterByUserId(userId);
		World world = worldRepository.findByCharacterId(character.getId())
				.orElseThrow(() -> new ResourceNotFoundException("월드를 찾을 수 없습니다."));
		return toResponse(world);
	}

	private Character findCharacterByUserId(Long userId) {
		return characterRepository.findByUserId(userId)
				.orElseThrow(() -> new IllegalArgumentException("캐릭터를 찾을 수 없습니다."));
	}

	private CreateWorldResponse toResponse(World world) {
		return new CreateWorldResponse(
				world.getId(),
				world.getWorldName(),
				world.getSeason(),
				world.getWeather(),
				world.getDay(),
				world.getGold(),
				world.getWood(),
				world.getStone(),
				world.getFood());
	}
}
