package com.projecteden.memorytaxonomy.legacy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.village.domain.VillageCategory;

@Component
public class LegacyMemoryTagMapper {

	public List<String> toTagCodes(RecognizedObject recognizedObject) {
		if (recognizedObject == null
				|| recognizedObject == RecognizedObject.UNKNOWN
				|| recognizedObject.getCategory() == VillageCategory.UNKNOWN) {
			return List.of();
		}

		Set<String> tagCodes = new LinkedHashSet<>();
		switch (recognizedObject) {
			case CAT -> tagCodes.add("CAT");
			case DOG -> tagCodes.add("DOG");
			case FLOWER -> tagCodes.add("FLOWER");
			case TREE -> tagCodes.add("TREE");
			case FOOD, BREAD, FRUIT, VEGETABLE, TOMATO, CARROT, POTATO, WHEAT ->
					tagCodes.add("FOOD");
			case WATER, RIVER, SEA, POND -> tagCodes.add("WATER");
			case ROAD, PATH, STREET -> {
				tagCodes.add("ROAD");
				tagCodes.add("WALKING");
			}
			case PARK -> {
				tagCodes.add("PARK");
				tagCodes.add("WALKING");
			}
			case BOOK, NOTEBOOK, READING, LECTURE, WRITING, LIBRARY, STUDY -> {
				tagCodes.add("BOOK");
				tagCodes.add("STUDYING");
			}
			case LAPTOP, COMPUTER, CODING, PROGRAMMING, DESK, OFFICE, MEETING, WORKSPACE -> {
				tagCodes.add("COMPUTER");
				tagCodes.add("WORKING");
			}
			default -> {
			}
		}
		return List.copyOf(tagCodes);
	}
}
