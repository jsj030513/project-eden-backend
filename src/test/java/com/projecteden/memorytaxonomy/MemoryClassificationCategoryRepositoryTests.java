package com.projecteden.memorytaxonomy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.memorytaxonomy.domain.MemoryClassification;
import com.projecteden.memorytaxonomy.domain.MemoryClassificationCategory;
import com.projecteden.memorytaxonomy.domain.MemoryClassificationCategoryRole;
import com.projecteden.memorytaxonomy.domain.MemoryTaxonomyCategory;
import com.projecteden.memorytaxonomy.domain.MemoryTaxonomyCategoryType;
import com.projecteden.memorytaxonomy.repository.MemoryClassificationCategoryRepository;
import com.projecteden.memorytaxonomy.repository.MemoryClassificationRepository;
import com.projecteden.memorytaxonomy.repository.MemoryClassificationTagRepository;
import com.projecteden.memorytaxonomy.repository.MemoryTaxonomyCategoryRepository;
import com.projecteden.photo.domain.Photo;
import com.projecteden.photo.repository.PhotoRepository;
import com.projecteden.user.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class MemoryClassificationCategoryRepositoryTests {

	private final MemoryClassificationRepository classifications;
	private final MemoryClassificationCategoryRepository classificationCategories;
	private final MemoryClassificationTagRepository classificationTags;
	private final MemoryTaxonomyCategoryRepository categories;
	private final PhotoRepository photos;
	private final MemoryClassificationTestFixtures fixtures;

	@Autowired
	MemoryClassificationCategoryRepositoryTests(
			MemoryClassificationRepository classifications,
			MemoryClassificationCategoryRepository classificationCategories,
			MemoryClassificationTagRepository classificationTags,
			MemoryTaxonomyCategoryRepository categories,
			UserRepository users,
			CharacterRepository characters,
			PhotoRepository photos) {
		this.classifications = classifications;
		this.classificationCategories = classificationCategories;
		this.classificationTags = classificationTags;
		this.categories = categories;
		this.photos = photos;
		this.fixtures = new MemoryClassificationTestFixtures(users, characters, photos);
	}

	@AfterEach
	void cleanUp() {
		classificationTags.deleteAllInBatch();
		classificationCategories.deleteAllInBatch();
		classifications.deleteAllInBatch();
		photos.deleteAllInBatch();
	}

	@Test
	void secondaryCategoryCanBeSaved() {
		MemoryClassification classification = classification();
		MemoryTaxonomyCategory category = category("WALK");

		MemoryClassificationCategory saved = classificationCategories.save(
				MemoryClassificationCategory.secondary(
						classification,
						category,
						BigDecimal.valueOf(0.8)));

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getRole()).isEqualTo(MemoryClassificationCategoryRole.SECONDARY);
		assertThat(saved.getConfidence()).isEqualByComparingTo("0.8000");
		assertThat(classificationCategories.existsByClassificationIdAndCategoryId(
				classification.getId(),
				category.getId()))
				.isTrue();
	}

	@Test
	void duplicateClassificationCategoryFails() {
		MemoryClassification classification = classification();
		MemoryTaxonomyCategory category = category("WALK");
		classificationCategories.saveAndFlush(
				MemoryClassificationCategory.secondary(classification, category, BigDecimal.valueOf(0.8)));

		assertThatThrownBy(() -> classificationCategories.saveAndFlush(
				MemoryClassificationCategory.secondary(classification, category, BigDecimal.valueOf(0.7))))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void sameCategoryCanBeSavedForDifferentClassifications() {
		MemoryTaxonomyCategory category = category("WALK");
		MemoryClassification first = classification();
		MemoryClassification second = classification();

		classificationCategories.saveAndFlush(
				MemoryClassificationCategory.secondary(first, category, BigDecimal.valueOf(0.8)));
		classificationCategories.saveAndFlush(
				MemoryClassificationCategory.secondary(second, category, BigDecimal.valueOf(0.7)));

		assertThat(classificationCategories.findAll()).hasSizeGreaterThanOrEqualTo(2);
	}

	@Test
	void categoriesCanBeFoundByClassification() {
		MemoryClassification classification = classification();
		MemoryTaxonomyCategory walk = category("WALK");
		MemoryTaxonomyCategory people = category("PEOPLE");
		MemoryClassificationCategory first = classificationCategories.save(
				MemoryClassificationCategory.secondary(classification, walk, BigDecimal.valueOf(0.8)));
		MemoryClassificationCategory second = classificationCategories.save(
				MemoryClassificationCategory.secondary(classification, people, BigDecimal.valueOf(0.7)));

		assertThat(classificationCategories.findAllByClassificationIdOrderByIdAsc(
				classification.getId()))
				.extracting(MemoryClassificationCategory::getId)
				.containsExactly(first.getId(), second.getId());
	}

	@Test
	void categoryConfidenceConstraintAllowsZeroOneAndNull() {
		MemoryClassification classification = classification();
		assertThat(classificationCategories.saveAndFlush(MemoryClassificationCategory.secondary(
				classification,
				category("ZERO"),
				BigDecimal.ZERO)).getId()).isNotNull();
		assertThat(classificationCategories.saveAndFlush(MemoryClassificationCategory.secondary(
				classification,
				category("ONE"),
				BigDecimal.ONE)).getId()).isNotNull();
		assertThat(classificationCategories.saveAndFlush(MemoryClassificationCategory.secondary(
				classification,
				category("NULL"),
				null)).getId()).isNotNull();
	}

	@Test
	void categoryConfidenceBelowZeroFails() {
		assertThatThrownBy(() -> classificationCategories.saveAndFlush(
				MemoryClassificationCategory.secondary(
						classification(),
						category("LOW"),
						BigDecimal.valueOf(-0.01))))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void categoryConfidenceAboveOneFails() {
		assertThatThrownBy(() -> classificationCategories.saveAndFlush(
				MemoryClassificationCategory.secondary(
						classification(),
						category("HIGH"),
						BigDecimal.valueOf(1.01))))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deletingClassificationDeletesCategoryRows() {
		MemoryClassification classification = classification();
		MemoryTaxonomyCategory category = category("WALK");
		classificationCategories.saveAndFlush(
				MemoryClassificationCategory.secondary(classification, category, BigDecimal.valueOf(0.8)));

		classifications.deleteById(classification.getId());
		classifications.flush();

		assertThat(classificationCategories.findAllByClassificationIdOrderByIdAsc(
				classification.getId()))
				.isEmpty();
	}

	@Test
	void categoryDeleteIsRestrictedWhenUsedByClassification() {
		MemoryClassification classification = classification();
		MemoryTaxonomyCategory category = category("WALK");
		classificationCategories.saveAndFlush(
				MemoryClassificationCategory.secondary(classification, category, BigDecimal.valueOf(0.8)));

		assertThatThrownBy(() -> {
			categories.deleteById(category.getId());
			categories.flush();
		})
				.isInstanceOf(RuntimeException.class);
	}

	@Test
	void deletingPhotoDeletesClassificationRows() {
		Photo photo = fixtures.photo();
		MemoryClassification classification = classifications.saveAndFlush(MemoryClassification.create(
				photo,
				null,
				null,
				null,
				null,
				null,
				"MOCK",
				null,
				false));

		photos.deleteById(photo.getId());
		photos.flush();

		assertThat(classifications.findById(classification.getId())).isEmpty();
	}

	private MemoryClassification classification() {
		return classifications.saveAndFlush(MemoryClassification.create(
				fixtures.photo(),
				null,
				null,
				null,
				null,
				null,
				"MOCK",
				null,
				false));
	}

	private MemoryTaxonomyCategory category(String code) {
		return categories.saveAndFlush(MemoryTaxonomyCategory.create(
				code + "_" + UUID.randomUUID().toString().replace("-", ""),
				code,
				MemoryTaxonomyCategoryType.ACTIVITY,
				10));
	}
}
