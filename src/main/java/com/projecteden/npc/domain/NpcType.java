package com.projecteden.npc.domain;

import com.projecteden.region.domain.RegionType;

public enum NpcType {
	VILLAGE_CHIEF("촌장 에반", "에덴 마을의 촌장", RegionType.VILLAGE),
	GARDENER("정원사 릴리", "꽃과 씨앗을 관리한다.", RegionType.FLOWER_FIELD),
	CARPENTER("목수 브람", "집 업그레이드를 담당한다.", RegionType.VILLAGE),
	MERCHANT("상인 노아", "아이템을 거래한다.", RegionType.VILLAGE),
	ARCHIVIST("기록관 루나", "에덴의 역사를 기록한다.", RegionType.VILLAGE);

	private final String npcName;
	private final String description;
	private final RegionType defaultRegionType;

	NpcType(String npcName, String description, RegionType defaultRegionType) {
		this.npcName = npcName;
		this.description = description;
		this.defaultRegionType = defaultRegionType;
	}

	public String getNpcName() {
		return npcName;
	}

	public String getDescription() {
		return description;
	}

	public RegionType getDefaultRegionType() {
		return defaultRegionType;
	}
}
