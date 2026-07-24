package com.projecteden.memorytaxonomy.legacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.ai.domain.Recognition;
import com.projecteden.ai.repository.RecognitionRepository;
import com.projecteden.ai.service.RecognitionApplicationService;
import com.projecteden.character.domain.Character;
import com.projecteden.character.domain.CharacterGender;
import com.projecteden.character.domain.CharacterJob;
import com.projecteden.character.domain.HairStyle;
import com.projecteden.character.domain.Outfit;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.evolution.repository.EvolutionHistoryRepository;
import com.projecteden.evolution.repository.WorldDecorationRepository;
import com.projecteden.evolution.repository.WorldEvolutionRepository;
import com.projecteden.memorytaxonomy.repository.MemoryClassificationCategoryRepository;
import com.projecteden.memorytaxonomy.repository.MemoryClassificationRepository;
import com.projecteden.memorytaxonomy.repository.MemoryClassificationTagRepository;
import com.projecteden.memorytaxonomy.repository.MemoryTaxonomyCategoryRepository;
import com.projecteden.photo.domain.Photo;
import com.projecteden.photo.repository.PhotoRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.village.repository.VillageChangeRepository;
import com.projecteden.village.repository.VillageHistoryRepository;
import com.projecteden.village.repository.VillageMemoryRepository;
import com.projecteden.village.repository.VillageThemeSnapshotRepository;

@SpringBootTest
@ActiveProfiles("test")
class LegacyRecognitionDualWriteIntegrationTests {

	@Autowired private RecognitionApplicationService recognitionApplicationService;
	@Autowired private ApplicationEventPublisher eventPublisher;
	@Autowired private TransactionTemplate transactionTemplate;
	@Autowired private RecognitionRepository recognitions;
	@Autowired private PhotoRepository photos;
	@Autowired private UserRepository users;
	@Autowired private CharacterRepository characters;
	@Autowired private MemoryClassificationRepository classifications;
	@Autowired private MemoryClassificationCategoryRepository classificationCategories;
	@Autowired private MemoryClassificationTagRepository classificationTags;
	@Autowired private MemoryTaxonomyCategoryRepository taxonomyCategories;
	@Autowired private VillageMemoryRepository villageMemories;
	@Autowired private VillageChangeRepository villageChanges;
	@Autowired private VillageHistoryRepository villageHistories;
	@Autowired private VillageThemeSnapshotRepository villageThemeSnapshots;
	@Autowired private EvolutionHistoryRepository evolutionHistories;
	@Autowired private WorldDecorationRepository worldDecorations;
	@Autowired private WorldEvolutionRepository worldEvolutions;

	@AfterEach
	void cleanUp() {
		classificationTags.deleteAllInBatch();
		classificationCategories.deleteAllInBatch();
		classifications.deleteAllInBatch();
		villageHistories.deleteAllInBatch();
		villageChanges.deleteAllInBatch();
		villageThemeSnapshots.deleteAllInBatch();
		villageMemories.deleteAllInBatch();
		evolutionHistories.deleteAllInBatch();
		worldDecorations.deleteAllInBatch();
		worldEvolutions.deleteAllInBatch();
		recognitions.deleteAllInBatch();
		photos.deleteAllInBatch();
		characters.deleteAllInBatch();
		users.deleteAllInBatch();
	}

	@Test
	void recognitionCommitCreatesLegacyClassificationAfterCommit() {
		User user = user();
		Character character = character(user);
		Photo photo = photo(character, "flower.jpg");

		recognitionApplicationService.recognizePhoto(user.getId(), photo.getId());

		assertThat(recognitions.count()).isEqualTo(1);
		assertThat(villageMemories.count()).isEqualTo(1);
		Long natureId = taxonomyCategoryId("NATURE");
		assertThat(classifications.findAllByPhotoIdOrderByCreatedAtDesc(photo.getId()))
				.hasSize(1)
				.first()
				.satisfies(classification -> {
					assertThat(classification.getPrimaryCategory().getId()).isEqualTo(natureId);
					assertThat(classification.getProvider()).isEqualTo("LEGACY_MOCK");
					assertThat(classification.getModelVersion()).isEqualTo("mock-filename-v1");
					assertThat(classification.getTaxonomyVersion()).isEqualTo("v1");
					assertThat(classification.isFallback()).isFalse();
				});
	}

	@Test
	void rollbackTransactionDoesNotCreateClassificationFromEvent() {
		Photo photo = photo(character(user()), "cat.jpg");
		Recognition recognition = recognitions.save(Recognition.create(
				photo,
				RecognizedObject.CAT,
				82,
				true));

		transactionTemplate.execute(status -> {
			eventPublisher.publishEvent(new LegacyRecognitionCompletedEvent(
					photo.getId(),
					recognition.getId()));
			status.setRollbackOnly();
			return null;
		});

		assertThat(classifications.findAllByRecognitionIdOrderByCreatedAtDesc(
				recognition.getId())).isEmpty();
	}

	@Test
	void duplicateEventsCreateOnlyOneClassification() {
		Photo photo = photo(character(user()), "dog.jpg");
		Recognition recognition = recognitions.save(Recognition.create(
				photo,
				RecognizedObject.DOG,
				82,
				true));

		publishCommittedEvent(photo.getId(), recognition.getId());
		publishCommittedEvent(photo.getId(), recognition.getId());

		assertThat(classifications.findAllByRecognitionIdOrderByCreatedAtDesc(
				recognition.getId())).hasSize(1);
	}

	@Test
	void listenerFailureDoesNotEscapeToCommittedLegacyTransaction() {
		assertThatCode(() -> publishCommittedEvent(999_999L, 999_999L))
				.doesNotThrowAnyException();
	}

	@Test
	void studyAndWorkRecognitionsAreDualWritten() {
		User studyUser = user();
		Photo studyPhoto = photo(character(studyUser), "study-book.jpg");
		User workUser = user();
		Photo workPhoto = photo(character(workUser), "coding-laptop.jpg");

		recognitionApplicationService.recognizePhoto(studyUser.getId(), studyPhoto.getId());
		recognitionApplicationService.recognizePhoto(workUser.getId(), workPhoto.getId());

		Long studyId = taxonomyCategoryId("STUDY");
		Long workId = taxonomyCategoryId("WORK");
		assertThat(classifications.findAllByPhotoIdOrderByCreatedAtDesc(studyPhoto.getId()))
				.first()
				.satisfies(classification ->
						assertThat(classification.getPrimaryCategory().getId()).isEqualTo(studyId));
		assertThat(classifications.findAllByPhotoIdOrderByCreatedAtDesc(workPhoto.getId()))
				.first()
				.satisfies(classification ->
						assertThat(classification.getPrimaryCategory().getId()).isEqualTo(workId));
	}

	@Test
	void unknownRecognitionIsDualWrittenAsFallback() {
		User user = user();
		Photo photo = photo(character(user), "IMG_0001.HEIC");

		recognitionApplicationService.recognizePhoto(user.getId(), photo.getId());

		assertThat(classifications.findAllByPhotoIdOrderByCreatedAtDesc(photo.getId()))
				.first()
				.satisfies(classification -> {
					assertThat(classification.getPrimaryCategory()).isNull();
					assertThat(classification.isFallback()).isTrue();
					assertThat(classification.getObservation())
							.containsEntry("recognizedObject", "UNKNOWN")
							.containsEntry("legacyCategory", "UNKNOWN")
							.containsEntry("recognized", false)
							.containsEntry("fallback", true);
				});
	}

	@Test
	void reusedRecognitionDoesNotCreateAdditionalClassification() {
		User user = user();
		Photo photo = photo(character(user), "flower.jpg");

		recognitionApplicationService.recognizePhoto(user.getId(), photo.getId());
		recognitionApplicationService.recognizePhoto(user.getId(), photo.getId());

		assertThat(recognitions.count()).isEqualTo(1);
		assertThat(classifications.findAllByPhotoIdOrderByCreatedAtDesc(photo.getId()))
				.hasSize(1);
	}

	private void publishCommittedEvent(Long photoId, Long recognitionId) {
		transactionTemplate.execute(status -> {
			eventPublisher.publishEvent(new LegacyRecognitionCompletedEvent(photoId, recognitionId));
			return null;
		});
	}

	private Long taxonomyCategoryId(String code) {
		return taxonomyCategories.findByCode(code).orElseThrow().getId();
	}

	private User user() {
		String suffix = UUID.randomUUID().toString();
		return users.save(new User(
				"legacy-dual-write-" + suffix + "@example.com",
				"password",
				"legacy-dual-write-" + suffix));
	}

	private Character character(User user) {
		return characters.save(Character.create(
				user,
				"에덴",
				CharacterGender.NONE,
				HairStyle.PIXEL_CUT,
				"#000000",
				Outfit.BASIC,
				CharacterJob.BEGINNER));
	}

	private Photo photo(Character character, String originalFileName) {
		String suffix = UUID.randomUUID().toString();
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
