package com.projecteden.memorytaxonomy.legacy;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.village.domain.VillageCategory;

@Component
public class LegacyVillageCategoryMapper {

	public Optional<String> toTaxonomyCategoryCode(RecognizedObject recognizedObject) {
		if (recognizedObject == null || recognizedObject == RecognizedObject.UNKNOWN) {
			return Optional.empty();
		}
		return toTaxonomyCategoryCode(recognizedObject.getCategory());
	}

	public Optional<String> toTaxonomyCategoryCode(VillageCategory category) {
		if (category == null || category == VillageCategory.UNKNOWN) {
			return Optional.empty();
		}
		return Optional.of(category.name());
	}

	public boolean shouldFallback(boolean recognized, RecognizedObject recognizedObject) {
		return !recognized || toTaxonomyCategoryCode(recognizedObject).isEmpty();
	}
}
