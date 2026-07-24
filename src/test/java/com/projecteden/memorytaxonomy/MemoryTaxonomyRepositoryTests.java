package com.projecteden.memorytaxonomy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.projecteden.memorytaxonomy.domain.MemoryTag;
import com.projecteden.memorytaxonomy.domain.MemoryTagType;
import com.projecteden.memorytaxonomy.domain.MemoryTaxonomyCategory;
import com.projecteden.memorytaxonomy.domain.MemoryTaxonomyCategoryType;
import com.projecteden.memorytaxonomy.repository.MemoryTagRepository;
import com.projecteden.memorytaxonomy.repository.MemoryTaxonomyCategoryRepository;

@SpringBootTest
@ActiveProfiles("test")
class MemoryTaxonomyRepositoryTests {

	private final MemoryTaxonomyCategoryRepository categories;
	private final MemoryTagRepository tags;

	@Autowired
	MemoryTaxonomyRepositoryTests(
			MemoryTaxonomyCategoryRepository categories,
			MemoryTagRepository tags) {
		this.categories = categories;
		this.tags = tags;
	}

	@BeforeEach
	void setUp() {
		tags.deleteAllInBatch();
		categories.deleteAllInBatch();
	}

	@Test
	void categoryCanBeSavedAndFoundByCode() {
		MemoryTaxonomyCategory category = categories.save(
				MemoryTaxonomyCategory.create("NATURE", "자연", MemoryTaxonomyCategoryType.DOMAIN, 10));

		MemoryTaxonomyCategory found = categories.findByCode("NATURE").orElseThrow();
		assertThat(found.getId()).isEqualTo(category.getId());
		assertThat(found.getDisplayName()).isEqualTo("자연");
		assertThat(categories.existsByCode("NATURE")).isTrue();
		assertThat(category.getTaxonomyVersion()).isEqualTo("v1");
	}

	@Test
	void categoryCodeMustBeUnique() {
		categories.saveAndFlush(
				MemoryTaxonomyCategory.create("NATURE", "자연", MemoryTaxonomyCategoryType.DOMAIN, 10));

		assertThatThrownBy(() -> categories.saveAndFlush(
				MemoryTaxonomyCategory.create("NATURE", "다른 자연", MemoryTaxonomyCategoryType.DOMAIN, 20)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void activeCategoriesAreSortedBySortOrder() {
		MemoryTaxonomyCategory inactive = MemoryTaxonomyCategory.create(
				"REST", "휴식", MemoryTaxonomyCategoryType.ACTIVITY, 1);
		inactive.deactivate();
		categories.save(inactive);
		categories.save(MemoryTaxonomyCategory.create(
				"NATURE", "자연", MemoryTaxonomyCategoryType.DOMAIN, 20));
		categories.save(MemoryTaxonomyCategory.create(
				"ANIMAL", "동물", MemoryTaxonomyCategoryType.DOMAIN, 10));

		assertThat(categories.findAllByActiveTrueOrderBySortOrderAscIdAsc())
				.extracting(MemoryTaxonomyCategory::getCode)
				.containsExactly("ANIMAL", "NATURE");
	}

	@Test
	void categoryCanHaveOptionalParent() {
		MemoryTaxonomyCategory parent = categories.save(
				MemoryTaxonomyCategory.create("PLACE", "장소", MemoryTaxonomyCategoryType.PLACE, 10));
		MemoryTaxonomyCategory child = categories.saveAndFlush(
				MemoryTaxonomyCategory.create(
						"WATER",
						"물가",
						parent,
						MemoryTaxonomyCategoryType.PLACE,
						20,
						"v1"));

		assertThat(categories.findByCode("WATER")).isPresent();
		assertThat(child.getParent().getId()).isEqualTo(parent.getId());
	}

	@Test
	void categoryCanBeFilteredByTaxonomyVersion() {
		categories.save(MemoryTaxonomyCategory.create(
				"NATURE", "자연", null, MemoryTaxonomyCategoryType.DOMAIN, 10, "v1"));
		categories.save(MemoryTaxonomyCategory.create(
				"FUTURE", "미래", null, MemoryTaxonomyCategoryType.DOMAIN, 20, "v2"));

		assertThat(categories.findAllByTaxonomyVersionAndActiveTrueOrderBySortOrderAscIdAsc("v1"))
				.extracting(MemoryTaxonomyCategory::getCode)
				.containsExactly("NATURE");
	}

	@Test
	void tagCanBeSavedAndFoundByCode() {
		MemoryTag tag = tags.save(MemoryTag.create("CAT", "고양이", MemoryTagType.SUBJECT));

		MemoryTag found = tags.findByCode("CAT").orElseThrow();
		assertThat(found.getId()).isEqualTo(tag.getId());
		assertThat(found.getDisplayName()).isEqualTo("고양이");
		assertThat(tags.existsByCode("CAT")).isTrue();
		assertThat(tag.getTaxonomyVersion()).isEqualTo("v1");
	}

	@Test
	void tagCodeMustBeUnique() {
		tags.saveAndFlush(MemoryTag.create("CAT", "고양이", MemoryTagType.SUBJECT));

		assertThatThrownBy(() -> tags.saveAndFlush(
				MemoryTag.create("CAT", "다른 고양이", MemoryTagType.SUBJECT)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void activeTagsAreSortedByCode() {
		MemoryTag inactive = MemoryTag.create("DOG", "강아지", MemoryTagType.SUBJECT);
		inactive.deactivate();
		tags.save(inactive);
		tags.save(MemoryTag.create("CAT", "고양이", MemoryTagType.SUBJECT));
		tags.save(MemoryTag.create("BOOK", "책", MemoryTagType.OBJECT));

		assertThat(tags.findAllByActiveTrueOrderByCodeAsc())
				.extracting(MemoryTag::getCode)
				.containsExactly("BOOK", "CAT");
	}

	@Test
	void tagCanBeFilteredByTaxonomyVersion() {
		tags.save(MemoryTag.create("CAT", "고양이", MemoryTagType.SUBJECT, "v1"));
		tags.save(MemoryTag.create("DRONE", "드론", MemoryTagType.OBJECT, "v2"));

		assertThat(tags.findAllByTaxonomyVersionAndActiveTrueOrderByCodeAsc("v1"))
				.extracting(MemoryTag::getCode)
				.containsExactly("CAT");
	}
}
