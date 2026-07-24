package com.projecteden.ai.domain;

import com.projecteden.village.domain.VillageCategory;

public enum RecognizedObject {
	FLOWER(VillageCategory.NATURE),
	TREE(VillageCategory.NATURE),
	PLANT(VillageCategory.NATURE),
	SKY(VillageCategory.NATURE),
	LANDSCAPE(VillageCategory.NATURE),
	CAT(VillageCategory.ANIMAL),
	DOG(VillageCategory.ANIMAL),
	BIRD(VillageCategory.ANIMAL),
	ANIMAL(VillageCategory.ANIMAL),
	FOOD(VillageCategory.FOOD),
	BREAD(VillageCategory.FOOD),
	FRUIT(VillageCategory.FOOD),
	VEGETABLE(VillageCategory.FOOD),
	TOMATO(VillageCategory.FOOD),
	CARROT(VillageCategory.FOOD),
	POTATO(VillageCategory.FOOD),
	WHEAT(VillageCategory.FOOD),
	WATER(VillageCategory.WATER),
	RIVER(VillageCategory.WATER),
	SEA(VillageCategory.WATER),
	POND(VillageCategory.WATER),
	ROAD(VillageCategory.WALK),
	PATH(VillageCategory.WALK),
	PARK(VillageCategory.WALK),
	STREET(VillageCategory.WALK),
	BOOK(VillageCategory.STUDY),
	NOTEBOOK(VillageCategory.STUDY),
	STUDY(VillageCategory.STUDY),
	READING(VillageCategory.STUDY),
	LECTURE(VillageCategory.STUDY),
	WRITING(VillageCategory.STUDY),
	LIBRARY(VillageCategory.STUDY),
	LAPTOP(VillageCategory.WORK),
	COMPUTER(VillageCategory.WORK),
	CODING(VillageCategory.WORK),
	PROGRAMMING(VillageCategory.WORK),
	DESK(VillageCategory.WORK),
	OFFICE(VillageCategory.WORK),
	MEETING(VillageCategory.WORK),
	WORKSPACE(VillageCategory.WORK),
	COFFEE(VillageCategory.UNKNOWN),
	ROOM(VillageCategory.UNKNOWN),
	DAILY_OBJECT(VillageCategory.UNKNOWN),
	PERSON(VillageCategory.UNKNOWN),
	FRIEND(VillageCategory.UNKNOWN),
	FAMILY(VillageCategory.UNKNOWN),
	OBJECT(VillageCategory.UNKNOWN),
	UNKNOWN(VillageCategory.UNKNOWN);

	private final VillageCategory category;

	RecognizedObject(VillageCategory category) {
		this.category = category;
	}

	public VillageCategory getCategory() {
		return category;
	}
}
