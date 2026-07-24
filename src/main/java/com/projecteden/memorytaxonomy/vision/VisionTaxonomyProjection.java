package com.projecteden.memorytaxonomy.vision;
import java.util.List;
public record VisionTaxonomyProjection(String version, List<VisionProjectionCandidate> categories, List<VisionProjectionCandidate> tags) {
	public static final String VERSION = "eden-vision-taxonomy-projection-v1";
	public VisionTaxonomyProjection { categories = List.copyOf(categories); tags = List.copyOf(tags); }
}
