package com.projecteden.memorytaxonomy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.projecteden.memorytaxonomy.config.MemoryTaxonomySeeder;
import com.projecteden.memorytaxonomy.domain.MemoryTag;
import com.projecteden.memorytaxonomy.domain.MemoryTagType;
import com.projecteden.memorytaxonomy.domain.MemoryTaxonomyCategory;
import com.projecteden.memorytaxonomy.domain.MemoryTaxonomyCategoryType;
import com.projecteden.memorytaxonomy.repository.MemoryTagRepository;
import com.projecteden.memorytaxonomy.repository.MemoryTaxonomyCategoryRepository;

@SpringBootTest
@ActiveProfiles("test")
class MemoryTaxonomySeedTests {

	private static final long DEFAULT_CATEGORY_COUNT = 21;
	private static final long DEFAULT_TAG_COUNT = 34;

	private final MemoryTaxonomySeeder seeder;
	private final MemoryTaxonomyCategoryRepository categories;
	private final MemoryTagRepository tags;

	@Autowired
	MemoryTaxonomySeedTests(
			MemoryTaxonomySeeder seeder,
			MemoryTaxonomyCategoryRepository categories,
			MemoryTagRepository tags) {
		this.seeder = seeder;
		this.categories = categories;
		this.tags = tags;
	}

	@BeforeEach
	void setUp() {
		tags.deleteAllInBatch();
		categories.deleteAllInBatch();
	}

	@Test
	void seedCreatesDefaultCategoriesAndTags() {
		seeder.seed();

		assertThat(categories.count()).isEqualTo(DEFAULT_CATEGORY_COUNT);
		assertThat(tags.count()).isEqualTo(DEFAULT_TAG_COUNT);
		assertThat(categories.findByCode("NATURE")).isPresent();
		assertThat(categories.findByCode("STUDY")).isPresent();
		assertThat(categories.findByCode("WORK")).isPresent();
		assertThat(categories.findByCode("PEOPLE")).isPresent();
		assertThat(tags.findByCode("CAT")).isPresent();
		assertThat(tags.findByCode("FRIENDS")).isPresent();
		assertThat(tags.findByCode("WATER")).isPresent();
	}

	@Test
	void seedIsIdempotent() {
		seeder.seed();
		seeder.seed();

		assertThat(categories.count()).isEqualTo(DEFAULT_CATEGORY_COUNT);
		assertThat(tags.count()).isEqualTo(DEFAULT_TAG_COUNT);
	}

	@Test
	void seedDoesNotOverwriteExistingCategory() {
		MemoryTaxonomyCategory existing = MemoryTaxonomyCategory.create(
				"NATURE", "운영자 자연", MemoryTaxonomyCategoryType.DOMAIN, 999);
		existing.deactivate();
		categories.save(existing);

		seeder.seed();

		MemoryTaxonomyCategory category = categories.findByCode("NATURE").orElseThrow();
		assertThat(category.getDisplayName()).isEqualTo("운영자 자연");
		assertThat(category.isActive()).isFalse();
		assertThat(categories.count()).isEqualTo(DEFAULT_CATEGORY_COUNT);
	}

	@Test
	void seedDoesNotOverwriteExistingTag() {
		MemoryTag existing = MemoryTag.create("CAT", "운영자 고양이", MemoryTagType.SUBJECT);
		existing.deactivate();
		tags.save(existing);

		seeder.seed();

		MemoryTag tag = tags.findByCode("CAT").orElseThrow();
		assertThat(tag.getDisplayName()).isEqualTo("운영자 고양이");
		assertThat(tag.isActive()).isFalse();
		assertThat(tags.count()).isEqualTo(DEFAULT_TAG_COUNT);
	}

	@Test
	void seededDataUsesV1TaxonomyVersion() {
		seeder.seed();

		assertThat(categories.findAll())
				.extracting(MemoryTaxonomyCategory::getTaxonomyVersion)
				.containsOnly("v1");
		assertThat(tags.findAll())
				.extracting(MemoryTag::getTaxonomyVersion)
				.containsOnly("v1");
	}

	@Test
	void seededCategoriesHaveNoParentYet() {
		seeder.seed();

		assertThat(categories.findAll())
				.extracting(MemoryTaxonomyCategory::getParent)
				.containsOnlyNulls();
	}
}
