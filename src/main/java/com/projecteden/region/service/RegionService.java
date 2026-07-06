package com.projecteden.region.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.character.domain.Character;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.region.domain.Region;
import com.projecteden.region.domain.RegionType;
import com.projecteden.region.dto.RegionResponse;
import com.projecteden.region.repository.RegionRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;

@Service
public class RegionService {

	private final RegionRepository regionRepository;
	private final WorldRepository worldRepository;
	private final CharacterRepository characterRepository;

	public RegionService(
			RegionRepository regionRepository,
			WorldRepository worldRepository,
			CharacterRepository characterRepository) {
		this.regionRepository = regionRepository;
		this.worldRepository = worldRepository;
		this.characterRepository = characterRepository;
	}

	@Transactional
	public void createDefaultRegions(Long worldId) {
		World world = worldRepository.findById(worldId)
				.orElseThrow(() -> new IllegalArgumentException("월드를 찾을 수 없습니다."));

		List<Region> regions = Arrays.stream(RegionType.values())
				.map(regionType -> Region.create(world, regionType))
				.toList();
		regionRepository.saveAll(regions);

		// TODO: 향후 NPC, Animal, Photo Spawn, Friend Visit, Season Event 시스템과 연결한다.
	}

	@Transactional(readOnly = true)
	public List<RegionResponse> getMyRegions(Long userId) {
		Character character = characterRepository.findByUserId(userId)
				.orElseThrow(() -> new IllegalArgumentException("캐릭터를 찾을 수 없습니다."));
		World world = worldRepository.findByCharacterId(character.getId())
				.orElseThrow(() -> new IllegalArgumentException("월드를 찾을 수 없습니다."));

		return regionRepository.findByWorldId(world.getId()).stream()
				.map(this::toResponse)
				.toList();
	}

	private RegionResponse toResponse(Region region) {
		return new RegionResponse(
				region.getId(),
				region.getRegionType(),
				region.getDisplayName(),
				region.isUnlocked());
	}
}
