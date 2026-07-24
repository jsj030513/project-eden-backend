package com.projecteden.memorytaxonomy.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.ai.domain.Recognition;
import com.projecteden.ai.repository.RecognitionRepository;
import com.projecteden.character.domain.Character;
import com.projecteden.character.domain.CharacterGender;
import com.projecteden.character.domain.CharacterJob;
import com.projecteden.character.domain.HairStyle;
import com.projecteden.character.domain.Outfit;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.memorytaxonomy.domain.MemoryClassification;
import com.projecteden.memorytaxonomy.repository.MemoryClassificationCategoryRepository;
import com.projecteden.memorytaxonomy.repository.MemoryClassificationRepository;
import com.projecteden.memorytaxonomy.repository.MemoryClassificationTagRepository;
import com.projecteden.memorytaxonomy.repository.MemoryTagRepository;
import com.projecteden.memorytaxonomy.repository.MemoryTaxonomyCategoryRepository;
import com.projecteden.photo.domain.Photo;
import com.projecteden.photo.repository.PhotoRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class LegacyMemoryClassificationWriterTests {

	@Autowired private LegacyMemoryClassificationWriter writer;
	@Autowired private PhotoRepository photos;
	@Autowired private RecognitionRepository recognitions;
	@Autowired private MemoryClassificationRepository classifications;
	@Autowired private MemoryClassificationCategoryRepository classificationCategories;
	@Autowired private MemoryClassificationTagRepository classificationTags;
	@Autowired private MemoryTaxonomyCategoryRepository taxonomyCategories;
	@Autowired private MemoryTagRepository tags;
	@Autowired private LegacyVillageCategoryMapper categoryMapper;
	@Autowired private LegacyMemoryTagMapper tagMapper;
	@Autowired private UserRepository users;
	@Autowired private CharacterRepository characters;

	@AfterEach
	void cleanUp() {
		classificationTags.deleteAllInBatch();
		classificationCategories.deleteAllInBatch();
		classifications.deleteAllInBatch();
		recognitions.deleteAllInBatch();
		photos.deleteAllInBatch();
		characters.deleteAllInBatch();
		users.deleteAllInBatch();
	}

	@Test
	void writesClassificationFromLegacyRecognition() {
		Photo photo = photo("flower.jpg");
		Recognition recognition = recognitions.save(Recognition.create(
				photo,
				RecognizedObject.FLOWER,
				95,
				true));

		MemoryClassification classification = writer
				.writeFromLegacyRecognition(photo.getId(), recognition.getId())
				.orElseThrow();

		assertThat(classification.getPhoto().getId()).isEqualTo(photo.getId());
		assertThat(classification.getRecognition().getId()).isEqualTo(recognition.getId());
		assertThat(classification.getPrimaryCategory().getCode()).isEqualTo("NATURE");
		assertThat(classificationCategories.findAllByClassificationIdOrderByIdAsc(
				classification.getId())).isEmpty();
		Long flowerTagId = tags.findByCode("FLOWER").orElseThrow().getId();
		assertThat(classificationTags.findAllByClassificationIdOrderByIdAsc(
				classification.getId()))
				.extracting(tag -> tag.getTag().getId())
				.containsExactly(flowerTagId);
		assertThat(classification.getProvider()).isEqualTo("LEGACY_MOCK");
		assertThat(classification.getModelVersion()).isEqualTo("mock-filename-v1");
		assertThat(classification.getTaxonomyVersion()).isEqualTo("v1");
		assertThat(classification.getConfidence()).isEqualByComparingTo("0.9500");
		assertThat(classification.isFallback()).isFalse();
		assertThat(classification.getObservation())
				.containsEntry("source", "legacy-recognition")
				.containsEntry("recognizedObject", "FLOWER")
				.containsEntry("legacyCategory", "NATURE")
				.containsEntry("recognized", true)
				.containsEntry("fallback", false);
	}

	@Test
	void unknownRecognitionCreatesFallbackClassificationWithoutPrimaryCategoryOrTags() {
		Photo photo = photo("unknown.heic");
		Recognition recognition = recognitions.save(Recognition.create(
				photo,
				RecognizedObject.UNKNOWN,
				0,
				false));

		MemoryClassification classification = writer
				.writeFromLegacyRecognition(photo.getId(), recognition.getId())
				.orElseThrow();

		assertThat(classification.getPrimaryCategory()).isNull();
		assertThat(classification.isFallback()).isTrue();
		assertThat(classificationTags.findAllByClassificationIdOrderByIdAsc(
				classification.getId())).isEmpty();
		assertThat(classification.getObservation())
				.containsEntry("recognizedObject", "UNKNOWN")
				.containsEntry("legacyCategory", "UNKNOWN")
				.containsEntry("recognized", false)
				.containsEntry("fallback", true);
	}

	@Test
	void sameRecognitionIsIdempotent() {
		Photo photo = photo("cat.jpg");
		Recognition recognition = recognitions.save(Recognition.create(
				photo,
				RecognizedObject.CAT,
				82,
				true));

		MemoryClassification first = writer
				.writeFromLegacyRecognition(photo.getId(), recognition.getId())
				.orElseThrow();
		MemoryClassification second = writer
				.writeFromLegacyRecognition(photo.getId(), recognition.getId())
				.orElseThrow();

		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(classifications.findAllByRecognitionIdOrderByCreatedAtDesc(
				recognition.getId())).hasSize(1);
	}

	@Test
	void missingCategoryCodeStoresFallbackClassification() {
		Photo photo = photo("flower.jpg");
		Recognition recognition = recognitions.save(Recognition.create(
				photo,
				RecognizedObject.FLOWER,
				95,
				true));
		LegacyMemoryClassificationWriter localWriter = new LegacyMemoryClassificationWriter(
				photos,
				recognitions,
				classifications,
				taxonomyCategories,
				tags,
				classificationTags,
				new LegacyVillageCategoryMapper() {
					@Override
					public Optional<String> toTaxonomyCategoryCode(RecognizedObject object) {
						return Optional.of("MISSING_CATEGORY");
					}
				},
				tagMapper);

		MemoryClassification classification = localWriter
				.writeFromLegacyRecognition(photo.getId(), recognition.getId())
				.orElseThrow();

		assertThat(classification.getPrimaryCategory()).isNull();
		assertThat(classification.isFallback()).isTrue();
	}

	@Test
	void missingTagCodeIsSkippedWithoutBlockingClassification() {
		Photo photo = photo("flower.jpg");
		Recognition recognition = recognitions.save(Recognition.create(
				photo,
				RecognizedObject.FLOWER,
				95,
				true));
		LegacyMemoryClassificationWriter localWriter = new LegacyMemoryClassificationWriter(
				photos,
				recognitions,
				classifications,
				taxonomyCategories,
				tags,
				classificationTags,
				categoryMapper,
				new LegacyMemoryTagMapper() {
					@Override
					public List<String> toTagCodes(RecognizedObject object) {
						return List.of("FLOWER", "MISSING_TAG");
					}
				});

		MemoryClassification classification = localWriter
				.writeFromLegacyRecognition(photo.getId(), recognition.getId())
				.orElseThrow();

		assertThat(classification.getPrimaryCategory().getCode()).isEqualTo("NATURE");
		Long flowerTagId = tags.findByCode("FLOWER").orElseThrow().getId();
		assertThat(classificationTags.findAllByClassificationIdOrderByIdAsc(
				classification.getId()))
				.extracting(tag -> tag.getTag().getId())
				.containsExactly(flowerTagId);
	}

	@Test
	void taxonomyLookupUsesCodeEvenWhenDisplayNameChanges() {
		var category = taxonomyCategories.findByCode("NATURE").orElseThrow();
		String originalDisplayName = category.getDisplayName();
		category.rename("운영자가 바꾼 자연 이름");
		taxonomyCategories.save(category);
		Photo photo = photo("flower.jpg");
		Recognition recognition = recognitions.save(Recognition.create(
				photo,
				RecognizedObject.FLOWER,
				95,
				true));

		try {
			MemoryClassification classification = writer
					.writeFromLegacyRecognition(photo.getId(), recognition.getId())
					.orElseThrow();

			assertThat(classification.getPrimaryCategory().getCode()).isEqualTo("NATURE");
			assertThat(classification.getPrimaryCategory().getDisplayName())
					.isEqualTo("운영자가 바꾼 자연 이름");
		} finally {
			category.rename(originalDisplayName);
			taxonomyCategories.save(category);
		}
	}

	private Photo photo(String originalFileName) {
		String suffix = UUID.randomUUID().toString();
		User user = users.save(new User(
				"legacy-classification-" + suffix + "@example.com",
				"password",
				"legacy-classification-" + suffix));
		Character character = characters.save(Character.create(
				user,
				"에덴",
				CharacterGender.NONE,
				HairStyle.PIXEL_CUT,
				"#000000",
				Outfit.BASIC,
				CharacterJob.BEGINNER));
		return photos.save(Photo.create(
				character,
				null,
				originalFileName,
				suffix + ".jpg",
				"image/jpeg",
				1024L,
				"/uploads/photos/" + suffix + ".jpg"));
	}
}
