package com.projecteden.memorytaxonomy.vision;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.projecteden.memorytaxonomy.observation.ImageObservation;
import com.projecteden.vision.config.VisionModelProperties;

/** Pure, non-persistent adapter from observation evidence to safe taxonomy candidates. */
@Component
public class VisionTaxonomyProjector {
	private static final Set<String> ANIMALS = Set.of("CAT", "DOG", "BIRD", "HORSE", "SHEEP", "COW");
	private final VisionModelProperties properties;
	public VisionTaxonomyProjector(VisionModelProperties properties) { this.properties = properties; }
	public VisionTaxonomyProjection project(ImageObservation observation) {
		if (observation == null || observation.fallback() || !observation.recognized()) return new VisionTaxonomyProjection(VisionTaxonomyProjection.VERSION, List.of(), List.of());
		List<VisionProjectionCandidate> categories = new ArrayList<>(), tags = new ArrayList<>();
		for (String object : observation.objects()) {
			if (ANIMALS.contains(object) && observation.confidence().floatValue() >= properties.getTaxonomy().getObjectThreshold()) categories.add(candidate(VisionProjectionSourceType.OBJECT, object, "CATEGORY", "ANIMAL", observation.confidence().floatValue(), "object-" + object.toLowerCase() + "-to-animal-v1", List.of(object)));
			if ("BOOK".equals(object) && observation.confidence().floatValue() >= properties.getTaxonomy().getObjectThreshold()) tags.add(candidate(VisionProjectionSourceType.OBJECT, object, "TAG", "BOOK", observation.confidence().floatValue(), "object-book-to-tag-v1", List.of("BOOK")));
		}
		for (String activity : observation.activities()) if ("READING".equals(activity) && observation.confidence().floatValue() >= properties.getTaxonomy().getActivityThreshold()) categories.add(candidate(VisionProjectionSourceType.ACTIVITY, activity, "CATEGORY", "STUDY", observation.confidence().floatValue(), "activity-reading-to-study-v1", List.of("PERSON", "BOOK", "PROXIMITY")));
		Comparator<VisionProjectionCandidate> order = Comparator.comparing(VisionProjectionCandidate::confidence).reversed().thenComparing(VisionProjectionCandidate::ruleId).thenComparing(VisionProjectionCandidate::targetCode);
		return new VisionTaxonomyProjection(VisionTaxonomyProjection.VERSION, categories.stream().sorted(order).toList(), tags.stream().sorted(order).toList());
	}
	private VisionProjectionCandidate candidate(VisionProjectionSourceType sourceType, String source, String targetType, String target, float confidence, String rule, List<String> evidence) { return new VisionProjectionCandidate(sourceType, source, targetType, target, confidence, rule, evidence); }
}
