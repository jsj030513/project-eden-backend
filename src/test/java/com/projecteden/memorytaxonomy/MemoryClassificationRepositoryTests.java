package com.projecteden.memorytaxonomy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.ai.domain.Recognition;
import com.projecteden.ai.repository.RecognitionRepository;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.memorytaxonomy.domain.MemoryClassification;
import com.projecteden.memorytaxonomy.domain.MemoryTaxonomyCategory;
import com.projecteden.memorytaxonomy.domain.MemoryTaxonomyCategoryType;
import com.projecteden.memorytaxonomy.repository.MemoryClassificationRepository;
import com.projecteden.memorytaxonomy.repository.MemoryClassificationCategoryRepository;
import com.projecteden.memorytaxonomy.repository.MemoryClassificationTagRepository;
import com.projecteden.memorytaxonomy.repository.MemoryTaxonomyCategoryRepository;
import com.projecteden.photo.domain.Photo;
import com.projecteden.photo.repository.PhotoRepository;
import com.projecteden.user.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class MemoryClassificationRepositoryTests {

	private final MemoryClassificationRepository classifications;
	private final MemoryClassificationCategoryRepository classificationCategories;
	private final MemoryClassificationTagRepository classificationTags;
	private final MemoryTaxonomyCategoryRepository categories;
	private final RecognitionRepository recognitions;
	private final PhotoRepository photos;
	private final MemoryClassificationTestFixtures fixtures;

	@Autowired
	MemoryClassificationRepositoryTests(
			MemoryClassificationRepository classifications,
			MemoryClassificationCategoryRepository classificationCategories,
			MemoryClassificationTagRepository classificationTags,
			MemoryTaxonomyCategoryRepository categories,
			RecognitionRepository recognitions,
			UserRepository users,
			CharacterRepository characters,
			PhotoRepository photos) {
		this.classifications = classifications;
		this.classificationCategories = classificationCategories;
		this.classificationTags = classificationTags;
		this.categories = categories;
		this.recognitions = recognitions;
		this.photos = photos;
		this.fixtures = new MemoryClassificationTestFixtures(users, characters, photos);
	}

	@AfterEach
	void cleanUp() {
		classificationTags.deleteAllInBatch();
		classificationCategories.deleteAllInBatch();
		classifications.deleteAllInBatch();
		recognitions.deleteAllInBatch();
		photos.deleteAllInBatch();
	}

	@Test
	void classificationCanBeSavedForPhoto() {
		Photo photo = fixtures.photo();
		MemoryClassification classification = classifications.save(classification(photo));

		assertThat(classification.getId()).isNotNull();
		assertThat(classifications.findAllByPhotoIdOrderByCreatedAtDesc(photo.getId()))
				.extracting(MemoryClassification::getId)
				.contains(classification.getId());
	}

	@Test
	void multipleClassificationsCanBeSavedForSamePhoto() {
		Photo photo = fixtures.photo();
		classifications.save(classification(photo));
		classifications.save(classification(photo));

		assertThat(classifications.findAllByPhotoIdOrderByCreatedAtDesc(photo.getId()))
				.hasSize(2);
	}

	@Test
	void latestClassificationCanBeFoundByPhoto() {
		Photo photo = fixtures.photo();
		MemoryClassification first = classifications.save(classification(photo));
		MemoryClassification second = classifications.save(classification(photo));

		assertThat(classifications.findFirstByPhotoIdOrderByCreatedAtDesc(photo.getId()))
				.isPresent()
				.get()
				.extracting(MemoryClassification::getId)
				.isEqualTo(second.getId());
		assertThat(first.getId()).isNotEqualTo(second.getId());
	}

	@Test
	void recognitionCanBeNull() {
		MemoryClassification classification = classifications.save(classification(fixtures.photo()));

		assertThat(classification.getRecognition()).isNull();
	}

	@Test
	void primaryCategoryCanBeNull() {
		Photo photo = fixtures.photo();
		MemoryClassification classification = classifications.save(MemoryClassification.create(
				photo,
				null,
				null,
				observation(),
				"분류 보류",
				BigDecimal.valueOf(0.5),
				"MOCK",
				"mock-v1",
				false));

		assertThat(classification.getPrimaryCategory()).isNull();
	}

	@Test
	void metadataCanBeSavedAndRead() {
		Photo photo = fixtures.photo();
		MemoryTaxonomyCategory category = categories.save(category("ANIMAL"));
		Recognition recognition = recognitions.save(Recognition.create(
				photo,
				RecognizedObject.FLOWER,
				95,
				true));
		MemoryClassification saved = classifications.save(MemoryClassification.create(
				photo,
				recognition,
				category,
				observation(),
				"친구와 공원에서 강아지를 산책하는 장면",
				BigDecimal.valueOf(0.95),
				"MOCK",
				"mock-v1",
				"v1",
				true));

		MemoryClassification found = classifications.findById(saved.getId()).orElseThrow();
		assertThat(found.getRecognition().getId()).isEqualTo(recognition.getId());
		assertThat(found.getPrimaryCategory().getId()).isEqualTo(category.getId());
		assertThat(found.getSummary()).isEqualTo("친구와 공원에서 강아지를 산책하는 장면");
		assertThat(found.getConfidence()).isEqualByComparingTo("0.9500");
		assertThat(found.getProvider()).isEqualTo("MOCK");
		assertThat(found.getModelVersion()).isEqualTo("mock-v1");
		assertThat(found.getTaxonomyVersion()).isEqualTo("v1");
		assertThat(found.isFallback()).isTrue();
		assertThat(found.getObservation())
				.containsEntry("scene", "park")
				.containsEntry("subjects", List.of("dog", "person"));
	}

	@Test
	void classificationsCanBeFoundByRecognition() {
		Photo photo = fixtures.photo();
		Recognition recognition = recognitions.save(Recognition.create(
				photo,
				RecognizedObject.FLOWER,
				95,
				true));
		MemoryClassification classification = classifications.save(MemoryClassification.create(
				photo,
				recognition,
				null,
				observation(),
				null,
				null,
				"MOCK",
				null,
				false));

		assertThat(classifications.findAllByRecognitionIdOrderByCreatedAtDesc(recognition.getId()))
				.extracting(MemoryClassification::getId)
				.containsExactly(classification.getId());
	}

	@Test
	void confidenceAllowsZeroOneAndNull() {
		Photo photo = fixtures.photo();
		assertThat(classifications.saveAndFlush(classification(photo, BigDecimal.ZERO)).getId())
				.isNotNull();
		assertThat(classifications.saveAndFlush(classification(photo, BigDecimal.ONE)).getId())
				.isNotNull();
		assertThat(classifications.saveAndFlush(classification(photo, null)).getId())
				.isNotNull();
	}

	@Test
	void confidenceBelowZeroFails() {
		assertThatThrownBy(() -> classifications.saveAndFlush(
				classification(fixtures.photo(), BigDecimal.valueOf(-0.01))))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void confidenceAboveOneFails() {
		assertThatThrownBy(() -> classifications.saveAndFlush(
				classification(fixtures.photo(), BigDecimal.valueOf(1.01))))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private MemoryClassification classification(Photo photo) {
		return classification(photo, BigDecimal.valueOf(0.95));
	}

	private MemoryClassification classification(Photo photo, BigDecimal confidence) {
		return MemoryClassification.create(
				photo,
				null,
				null,
				observation(),
				"테스트 분류",
				confidence,
				"MOCK",
				"mock-v1",
				false);
	}

	private MemoryTaxonomyCategory category(String code) {
		return MemoryTaxonomyCategory.create(
				code + "_" + UUID.randomUUID().toString().replace("-", ""),
				code,
				MemoryTaxonomyCategoryType.DOMAIN,
				10);
	}

	private Map<String, Object> observation() {
		return Map.of(
				"subjects", List.of("dog", "person"),
				"objects", List.of("leash", "tree"),
				"scene", "park",
				"activities", List.of("walking"),
				"relationships", List.of("friends"),
				"moodSignals", List.of("warm", "calm"));
	}
}
