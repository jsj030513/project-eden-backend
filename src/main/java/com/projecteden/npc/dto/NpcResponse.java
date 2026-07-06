package com.projecteden.npc.dto;

import com.projecteden.npc.domain.NpcType;
import com.projecteden.region.domain.RegionType;

public record NpcResponse(
		Long id,
		NpcType npcType,
		String npcName,
		String description,
		RegionType regionType) {
}
