package com.projecteden.region.domain;

public enum RegionType {
	VILLAGE("마을"),
	FOREST("숲"),
	RIVER("강"),
	HILL("언덕"),
	FLOWER_FIELD("꽃밭");

	private final String displayName;

	RegionType(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}
