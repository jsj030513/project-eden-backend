package com.projecteden.npc.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.character.domain.Character;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.npc.domain.Npc;
import com.projecteden.npc.domain.NpcType;
import com.projecteden.npc.dto.NpcResponse;
import com.projecteden.npc.repository.NpcRepository;
import com.projecteden.region.domain.Region;
import com.projecteden.region.repository.RegionRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;

@Service
public class NpcService {

	private final NpcRepository npcRepository;
	private final RegionRepository regionRepository;
	private final WorldRepository worldRepository;
	private final CharacterRepository characterRepository;

	public NpcService(
			NpcRepository npcRepository,
			RegionRepository regionRepository,
			WorldRepository worldRepository,
			CharacterRepository characterRepository) {
		this.npcRepository = npcRepository;
		this.regionRepository = regionRepository;
		this.worldRepository = worldRepository;
		this.characterRepository = characterRepository;
	}

	@Transactional
	public void createDefaultNpcs(Long worldId) {
		List<Npc> npcs = Arrays.stream(NpcType.values())
				.map(npcType -> Npc.create(findRegion(worldId, npcType), npcType))
				.toList();
		npcRepository.saveAll(npcs);

		// TODO: 향후 Quest, Dialogue, Shop, Friend Visit, AI Story, Season Event와 연결한다.
	}

	@Transactional(readOnly = true)
	public List<NpcResponse> getMyNpcs(Long userId) {
		Character character = characterRepository.findByUserId(userId)
				.orElseThrow(() -> new IllegalArgumentException("캐릭터를 찾을 수 없습니다."));
		World world = worldRepository.findByCharacterId(character.getId())
				.orElseThrow(() -> new IllegalArgumentException("월드를 찾을 수 없습니다."));

		return npcRepository.findByRegionWorldId(world.getId()).stream()
				.map(this::toResponse)
				.toList();
	}

	private Region findRegion(Long worldId, NpcType npcType) {
		return regionRepository.findByWorldIdAndRegionType(worldId, npcType.getDefaultRegionType())
				.orElseThrow(() -> new IllegalArgumentException("지역을 찾을 수 없습니다."));
	}

	private NpcResponse toResponse(Npc npc) {
		return new NpcResponse(
				npc.getId(),
				npc.getNpcType(),
				npc.getNpcName(),
				npc.getDescription(),
				npc.getRegion().getRegionType());
	}
}
