package com.projecteden.memorytaxonomy.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.projecteden.ai.domain.RecognizedObject;
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
import com.projecteden.memorytaxonomy.observation.openai.OpenAIObservationClient;
import com.projecteden.memorytaxonomy.observation.openai.OpenAIObservationRequest;
import com.projecteden.memorytaxonomy.observation.openai.OpenAIObservationResponse;
import com.projecteden.memorytaxonomy.repository.MemoryClassificationCategoryRepository;
import com.projecteden.memorytaxonomy.repository.MemoryClassificationRepository;
import com.projecteden.memorytaxonomy.repository.MemoryClassificationTagRepository;
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
@TestPropertySource(properties = {
		"eden.image-observation.provider=openai",
		"eden.image-observation.openai.api-key=test-key",
		"eden.image-observation.openai.model=test-model"
})
class OpenAIProviderRecognitionFallbackIntegrationTests {

	@Autowired private RecognitionApplicationService recognitionApplicationService;
	@Autowired private RecognitionRepository recognitions;
	@Autowired private PhotoRepository photos;
	@Autowired private UserRepository users;
	@Autowired private CharacterRepository characters;
	@Autowired private MemoryClassificationRepository classifications;
	@Autowired private MemoryClassificationCategoryRepository classificationCategories;
	@Autowired private MemoryClassificationTagRepository classificationTags;
	@Autowired private VillageMemoryRepository villageMemories;
	@Autowired private VillageChangeRepository villageChanges;
	@Autowired private VillageHistoryRepository villageHistories;
	@Autowired private VillageThemeSnapshotRepository villageThemeSnapshots;
	@Autowired private EvolutionHistoryRepository evolutionHistories;
	@Autowired private WorldDecorationRepository worldDecorations;
	@Autowired private WorldEvolutionRepository worldEvolutions;

	@MockitoBean private OpenAIObservationClient openAIObservationClient;

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
	void openAIProviderSelectionFallsBackToMockWhenPhotoBytesAreUnavailable() {
		User user = user();
		Photo photo = photo(character(user), "cat.jpg");

		var response = recognitionApplicationService.recognizePhoto(user.getId(), photo.getId());

		assertThat(response.recognizedObject()).isEqualTo(RecognizedObject.CAT);
		assertThat(response.recognized()).isTrue();
		assertThat(recognitions.count()).isEqualTo(1);
		assertThat(classifications.findAllByPhotoIdOrderByCreatedAtDesc(photo.getId()))
				.hasSize(1);
		verifyNoInteractions(openAIObservationClient);
	}

	@Test
	void recognizePhotoWithImagePassesBytesToOpenAIAndKeepsSingleClassificationPersistence() {
		User user = user();
		Photo photo = photo(character(user), "dog.jpg");
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"dog.jpg",
				"image/jpeg",
				validJpeg());
		when(openAIObservationClient.observe(any(OpenAIObservationRequest.class)))
				.thenReturn(new OpenAIObservationResponse(
						true,
						BigDecimal.valueOf(0.91),
						List.of("DOG"),
						List.of(),
						null,
						List.of("WALKING"),
						List.of(),
						List.of()));

		var response = recognitionApplicationService.recognizePhotoWithImage(
				user.getId(),
				photo.getId(),
				file);
		var secondResponse = recognitionApplicationService.recognizePhotoWithImage(
				user.getId(),
				photo.getId(),
				file);

		assertThat(response.recognizedObject()).isEqualTo(RecognizedObject.DOG);
		assertThat(response.recognized()).isTrue();
		assertThat(secondResponse.id()).isEqualTo(response.id());
		assertThat(recognitions.count()).isEqualTo(1);
		assertThat(classifications.findAllByPhotoIdOrderByCreatedAtDesc(photo.getId()))
				.hasSize(1);
		ArgumentCaptor<OpenAIObservationRequest> request = ArgumentCaptor.forClass(OpenAIObservationRequest.class);
		verify(openAIObservationClient, times(1)).observe(request.capture());
		assertThat(request.getValue().imageDataUrl()).startsWith("data:image/jpeg;base64,");
	}

	private User user() {
		String suffix = UUID.randomUUID().toString();
		return users.save(new User(
				"openai-fallback-" + suffix + "@example.com",
				"password",
				"openai-fallback-" + suffix));
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

	private byte[] validJpeg() {
		try {
			BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
			image.setRGB(0, 0, Color.RED.getRGB());
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			assertThat(ImageIO.write(image, "jpeg", output)).isTrue();
			return output.toByteArray();
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
