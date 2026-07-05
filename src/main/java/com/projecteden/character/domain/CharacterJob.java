package com.projecteden.character.domain;

public enum CharacterJob {
	BEGINNER(WeaponType.NONE),
	FARMER(WeaponType.HOE),
	EXPLORER(WeaponType.COMPASS),
	GUARDIAN(WeaponType.SHIELD),
	MERCHANT(WeaponType.BAG),
	BREEDER(WeaponType.FEED_BASKET),
	WIZARD(WeaponType.STAFF),
	WARRIOR(WeaponType.SWORD),
	ARCHER(WeaponType.BOW),
	BUILDER(WeaponType.HAMMER);

	private final WeaponType defaultWeaponType;

	CharacterJob(WeaponType defaultWeaponType) {
		this.defaultWeaponType = defaultWeaponType;
	}

	public WeaponType getDefaultWeaponType() {
		return defaultWeaponType;
	}
}
