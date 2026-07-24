package com.projecteden.memorytaxonomy.classification;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.memorytaxonomy.domain.MemoryTag;
import com.projecteden.memorytaxonomy.domain.MemoryTaxonomyCategory;
import com.projecteden.memorytaxonomy.observation.ImageObservation;
import com.projecteden.memorytaxonomy.repository.MemoryTagRepository;
import com.projecteden.memorytaxonomy.repository.MemoryTaxonomyCategoryRepository;
import com.projecteden.memorytaxonomy.vision.VisionTaxonomyProjector;

@Service
public class MemoryClassificationService {

	private final MemoryTaxonomyCategoryRepository categories;
	private final MemoryTagRepository tags;
	private final VisionTaxonomyProjector visionTaxonomyProjector;

	public MemoryClassificationService(
			MemoryTaxonomyCategoryRepository categories,
			MemoryTagRepository tags,
			VisionTaxonomyProjector visionTaxonomyProjector) {
		this.categories = categories;
		this.tags = tags;
		this.visionTaxonomyProjector = visionTaxonomyProjector;
	}

	@Transactional(readOnly = true)
	public MemoryClassificationResult classify(ImageObservation observation) {
		if (observation == null || observation.fallback() || !observation.recognized()) {
			return fallbackResult(observation);
		}

		var visionProjection = visionTaxonomyProjector.project(observation);
		LinkedHashSet<String> categoryCandidates = new LinkedHashSet<>();
		visionProjection.categories().forEach(candidate -> categoryCandidates.add(candidate.targetCode()));
		categoryCandidates.addAll(categoriesFromSignals(observation.subjects()));
		categoryCandidates.addAll(categoriesFromSignals(observation.objects()));
		categoryCandidates.add(categoryFromSignal(observation.scene()).orElse(null));
		categoryCandidates.addAll(categoriesFromSignals(observation.activities()));
		categoryCandidates.addAll(categoriesFromSignals(observation.relationships()));
		categoryCandidates.remove(null);

		List<String> activeCategories = categoryCandidates.stream()
				.filter(this::isActiveCategory)
				.toList();
		if (activeCategories.isEmpty()) {
			return fallbackResult(observation);
		}

		String primaryCategory = activeCategories.getFirst();
		List<String> secondaryCategories = activeCategories.stream()
				.filter(category -> !category.equals(primaryCategory))
				.toList();
		List<String> tagCodes = new ArrayList<>(tagCodes(observation));
		visionProjection.tags().forEach(candidate -> { if (isActiveTag(candidate.targetCode()) && !tagCodes.contains(candidate.targetCode())) tagCodes.add(candidate.targetCode()); });
		return new MemoryClassificationResult(
				primaryCategory,
				secondaryCategories,
				tagCodes,
				null,
				false,
				observation.confidence(),
				observation.toMap());
	}

	private MemoryClassificationResult fallbackResult(ImageObservation observation) {
		return new MemoryClassificationResult(
				null,
				List.of(),
				List.of(),
				null,
				true,
				observation == null ? null : observation.confidence(),
				observation == null ? Map.of() : observation.toMap());
	}

	private List<String> categoriesFromSignals(List<String> signals) {
		return signals.stream()
				.map(this::categoryFromSignal)
				.flatMap(Optional::stream)
				.toList();
	}

	private Optional<String> categoryFromSignal(String signal) {
		if (signal == null) {
			return Optional.empty();
		}
		return switch (signal) {
			case "CAT", "DOG", "BIRD", "ANIMAL" -> Optional.of("ANIMAL");
			case "FLOWER", "TREE", "PLANT", "SKY", "LANDSCAPE" -> Optional.of("NATURE");
			case "FOOD", "BREAD", "FRUIT", "VEGETABLE", "TOMATO", "CARROT", "POTATO", "WHEAT" ->
					Optional.of("FOOD");
			case "WATER", "RIVER", "SEA", "POND" -> Optional.of("WATER");
			case "ROAD", "PATH", "PARK", "STREET", "WALKING" -> Optional.of("WALK");
			case "BOOK", "NOTEBOOK", "STUDY", "READING", "LECTURE", "WRITING", "LIBRARY", "STUDYING" ->
					Optional.of("STUDY");
			case "LAPTOP", "COMPUTER", "CODING", "PROGRAMMING", "DESK", "OFFICE", "MEETING", "WORKSPACE", "WORKING" ->
					Optional.of("WORK");
			default -> Optional.empty();
		};
	}

	private List<String> tagCodes(ImageObservation observation) {
		LinkedHashSet<String> tagCodes = new LinkedHashSet<>();
		addIfActive(tagCodes, observation.subjects());
		addIfActive(tagCodes, observation.objects());
		addIfActive(tagCodes, observation.activities());
		if (observation.scene() != null) {
			addIfActive(tagCodes, List.of(observation.scene()));
		}
		return new ArrayList<>(tagCodes);
	}

	private void addIfActive(LinkedHashSet<String> tagCodes, List<String> signals) {
		for (String signal : signals) {
			String tagCode = tagCode(signal);
			if (tagCode != null && isActiveTag(tagCode)) {
				tagCodes.add(tagCode);
			}
		}
	}

	private String tagCode(String signal) {
		return switch (signal) {
			case "CAT", "DOG", "FLOWER", "TREE", "FOOD", "BOOK", "COMPUTER", "PARK", "ROAD", "WATER" -> signal;
			case "BREAD", "FRUIT", "VEGETABLE", "TOMATO", "CARROT", "POTATO", "WHEAT" -> "FOOD";
			case "RIVER", "SEA", "POND" -> "WATER";
			case "PATH", "STREET" -> "ROAD";
			case "STUDY", "READING", "LECTURE", "WRITING", "LIBRARY", "STUDYING" -> "STUDYING";
			case "LAPTOP", "CODING", "PROGRAMMING", "DESK", "OFFICE", "MEETING", "WORKSPACE", "WORKING" -> "WORKING";
			default -> null;
		};
	}

	private boolean isActiveCategory(String code) {
		return categories.findByCode(code)
				.map(MemoryTaxonomyCategory::isActive)
				.orElse(false);
	}

	private boolean isActiveTag(String code) {
		return tags.findByCode(code)
				.map(MemoryTag::isActive)
				.orElse(false);
	}
}
