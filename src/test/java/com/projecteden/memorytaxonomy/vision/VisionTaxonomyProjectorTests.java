package com.projecteden.memorytaxonomy.vision;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.projecteden.memorytaxonomy.observation.ImageObservation;
import com.projecteden.vision.config.VisionModelProperties;

class VisionTaxonomyProjectorTests {
	private final VisionTaxonomyProjector projector = new VisionTaxonomyProjector(new VisionModelProperties());
	@Test void projectsAnimalObjectsButNotAmbiguousObjects() {
		var cat = projector.project(observation(List.of("CAT"), List.of(), .74));
		assertThat(cat.categories()).extracting(VisionProjectionCandidate::targetCode).containsExactly("ANIMAL");
		assertThat(projector.project(observation(List.of("LAPTOP", "BICYCLE", "CUP"), List.of(), .9)).categories()).isEmpty();
	}
	@Test void bookOnlyAddsTagAndReadingProjectsStudy() {
		var book = projector.project(observation(List.of("BOOK"), List.of(), .9));
		assertThat(book.categories()).isEmpty();
		assertThat(book.tags()).extracting(VisionProjectionCandidate::targetCode).containsExactly("BOOK");
		assertThat(projector.project(observation(List.of("BOOK"), List.of("READING"), .8)).categories()).extracting(VisionProjectionCandidate::targetCode).containsExactly("STUDY");
	}
	@Test void leavesRelationshipsAndAmbiguousActivitiesUnmappedDeterministically() {
		var observation = ImageObservation.recognized(List.of("PERSON"), List.of("CAT"), null, List.of("WORK_OR_STUDY", "EATING_OR_CAFE"), List.of("PERSON_WITH_CAT"), List.of(), "LOCAL_YOLOX", "yolox", BigDecimal.valueOf(.9));
		var first = projector.project(observation);
		for (int index = 0; index < 20; index++) assertThat(projector.project(observation)).isEqualTo(first);
		assertThat(first.categories()).extracting(VisionProjectionCandidate::sourceCode).containsExactly("CAT");
	}
	private ImageObservation observation(List<String> objects, List<String> activities, double confidence) { return ImageObservation.recognized(List.of(), objects, null, activities, List.of(), List.of(), "LOCAL_YOLOX", "yolox", BigDecimal.valueOf(confidence)); }
}
