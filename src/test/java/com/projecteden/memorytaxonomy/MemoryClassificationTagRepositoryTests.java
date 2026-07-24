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
import com.projecteden.memorytaxonomy.domain.MemoryClassificationTag;
import com.projecteden.memorytaxonomy.domain.MemoryTag;
import com.projecteden.memorytaxonomy.domain.MemoryTagType;
import com.projecteden.memorytaxonomy.repository.MemoryClassificationRepository;
import com.projecteden.memorytaxonomy.repository.MemoryClassificationCategoryRepository;
import com.projecteden.memorytaxonomy.repository.MemoryClassificationTagRepository;
import com.projecteden.memorytaxonomy.repository.MemoryTagRepository;
import com.projecteden.photo.repository.PhotoRepository;
import com.projecteden.photo.repository.PhotoRepository;
import com.projecteden.user.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class MemoryClassificationTagRepositoryTests {

	private final MemoryClassificationRepository classifications;
	private final MemoryClassificationCategoryRepository classificationCategories;
	private final MemoryClassificationTagRepository classificationTags;
	private final MemoryTagRepository tags;
	private final PhotoRepository photos;
	private final MemoryClassificationTestFixtures fixtures;

	@Autowired
	MemoryClassificationTagRepositoryTests(
			MemoryClassificationRepository classifications,
			MemoryClassificationCategoryRepository classificationCategories,
			MemoryClassificationTagRepository classificationTags,
			MemoryTagRepository tags,
			UserRepository users,
			CharacterRepository characters,
			PhotoRepository photos) {
		this.classifications = classifications;
		this.classificationCategories = classificationCategories;
		this.classificationTags = classificationTags;
		this.tags = tags;
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
	void tagCanBeSaved() {
		MemoryClassification classification = classification();
		MemoryTag tag = tag("DOG");

		MemoryClassificationTag saved = classificationTags.save(
				MemoryClassificationTag.create(classification, tag, BigDecimal.valueOf(0.9)));

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getConfidence()).isEqualByComparingTo("0.9000");
		assertThat(classificationTags.existsByClassificationIdAndTagId(
				classification.getId(),
				tag.getId()))
				.isTrue();
	}

	@Test
	void duplicateClassificationTagFails() {
		MemoryClassification classification = classification();
		MemoryTag tag = tag("DOG");
		classificationTags.saveAndFlush(
				MemoryClassificationTag.create(classification, tag, BigDecimal.valueOf(0.9)));

		assertThatThrownBy(() -> classificationTags.saveAndFlush(
				MemoryClassificationTag.create(classification, tag, BigDecimal.valueOf(0.8))))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void sameTagCanBeSavedForDifferentClassifications() {
		MemoryTag tag = tag("DOG");
		MemoryClassification first = classification();
		MemoryClassification second = classification();

		classificationTags.saveAndFlush(
				MemoryClassificationTag.create(first, tag, BigDecimal.valueOf(0.9)));
		classificationTags.saveAndFlush(
				MemoryClassificationTag.create(second, tag, BigDecimal.valueOf(0.8)));

		assertThat(classificationTags.findAll()).hasSizeGreaterThanOrEqualTo(2);
	}

	@Test
	void tagsCanBeFoundByClassification() {
		MemoryClassification classification = classification();
		MemoryTag dog = tag("DOG");
		MemoryTag park = tag("PARK");
		MemoryClassificationTag first = classificationTags.save(
				MemoryClassificationTag.create(classification, dog, BigDecimal.valueOf(0.9)));
		MemoryClassificationTag second = classificationTags.save(
				MemoryClassificationTag.create(classification, park, BigDecimal.valueOf(0.8)));

		assertThat(classificationTags.findAllByClassificationIdOrderByIdAsc(classification.getId()))
				.extracting(MemoryClassificationTag::getId)
				.containsExactly(first.getId(), second.getId());
	}

	@Test
	void tagConfidenceConstraintAllowsZeroOneAndNull() {
		MemoryClassification classification = classification();
		assertThat(classificationTags.saveAndFlush(MemoryClassificationTag.create(
				classification,
				tag("ZERO"),
				BigDecimal.ZERO)).getId()).isNotNull();
		assertThat(classificationTags.saveAndFlush(MemoryClassificationTag.create(
				classification,
				tag("ONE"),
				BigDecimal.ONE)).getId()).isNotNull();
		assertThat(classificationTags.saveAndFlush(MemoryClassificationTag.create(
				classification,
				tag("NULL"),
				null)).getId()).isNotNull();
	}

	@Test
	void tagConfidenceBelowZeroFails() {
		assertThatThrownBy(() -> classificationTags.saveAndFlush(
				MemoryClassificationTag.create(
						classification(),
						tag("LOW"),
						BigDecimal.valueOf(-0.01))))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void tagConfidenceAboveOneFails() {
		assertThatThrownBy(() -> classificationTags.saveAndFlush(
				MemoryClassificationTag.create(
						classification(),
						tag("HIGH"),
						BigDecimal.valueOf(1.01))))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deletingClassificationDeletesTagRows() {
		MemoryClassification classification = classification();
		MemoryTag tag = tag("DOG");
		classificationTags.saveAndFlush(
				MemoryClassificationTag.create(classification, tag, BigDecimal.valueOf(0.9)));

		classifications.deleteById(classification.getId());
		classifications.flush();

		assertThat(classificationTags.findAllByClassificationIdOrderByIdAsc(classification.getId()))
				.isEmpty();
	}

	@Test
	void tagDeleteIsRestrictedWhenUsedByClassification() {
		MemoryClassification classification = classification();
		MemoryTag tag = tag("DOG");
		classificationTags.saveAndFlush(
				MemoryClassificationTag.create(classification, tag, BigDecimal.valueOf(0.9)));

		assertThatThrownBy(() -> {
			tags.deleteById(tag.getId());
			tags.flush();
		})
				.isInstanceOf(RuntimeException.class);
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

	private MemoryTag tag(String code) {
		return tags.saveAndFlush(MemoryTag.create(
				code + "_" + UUID.randomUUID().toString().replace("-", ""),
				code,
				MemoryTagType.SUBJECT));
	}
}
