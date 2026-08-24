package com.projecteden.ai;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.projecteden.ai.repository.RecognitionRepository;
import com.projecteden.auth.jwt.JwtTokenProvider;
import com.projecteden.character.domain.Character;
import com.projecteden.character.domain.CharacterGender;
import com.projecteden.character.domain.CharacterJob;
import com.projecteden.character.domain.HairStyle;
import com.projecteden.character.domain.Outfit;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.photo.domain.Photo;
import com.projecteden.photo.repository.PhotoRepository;
import com.projecteden.plant.domain.Plant;
import com.projecteden.plant.domain.PlantStage;
import com.projecteden.plant.repository.PlantRepository;
import com.projecteden.region.domain.Region;
import com.projecteden.region.domain.RegionType;
import com.projecteden.region.repository.RegionRepository;
import com.projecteden.seed.domain.SeedType;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.npc.NpcAffinityEventRepository;
import com.projecteden.world.npc.NpcAffinityStateRepository;
import com.projecteden.world.npc.NpcQuestEventRepository;
import com.projecteden.world.npc.NpcQuestStateRepository;
import com.projecteden.world.repository.WorldRepository;
import com.projecteden.world.ecology.WorldChangeRepository;
import com.projecteden.world.ecology.WorldPlacedObjectRepository;
import com.projecteden.evolution.repository.WorldEvolutionRepository;
import com.projecteden.evolution.repository.WorldDecorationRepository;
import com.projecteden.evolution.repository.EvolutionHistoryRepository;
import com.projecteden.village.repository.VillageMemoryRepository;
import com.projecteden.village.repository.VillageChangeRepository;
import com.projecteden.village.repository.VillageHistoryRepository;
import com.projecteden.village.repository.VillageThemeSnapshotRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecognitionIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RecognitionRepository recognitionRepository;

	@Autowired private EvolutionHistoryRepository evolutionHistoryRepository;
	@Autowired private WorldDecorationRepository worldDecorationRepository;
	@Autowired private WorldEvolutionRepository worldEvolutionRepository;
	@Autowired private VillageHistoryRepository villageHistoryRepository;
	@Autowired private VillageChangeRepository villageChangeRepository;
	@Autowired private VillageMemoryRepository villageMemoryRepository;
	@Autowired private VillageThemeSnapshotRepository villageThemeSnapshotRepository;

	@Autowired
	private PhotoRepository photoRepository;

	@Autowired
	private PlantRepository plantRepository;

	@Autowired
	private RegionRepository regionRepository;

	@Autowired
	private WorldRepository worldRepository;
	@Autowired private WorldPlacedObjectRepository worldPlacedObjectRepository;
	@Autowired private WorldChangeRepository worldChangeRepository;

	@Autowired private NpcQuestEventRepository npcQuestEventRepository;
	@Autowired private NpcQuestStateRepository npcQuestStateRepository;
	@Autowired private NpcAffinityEventRepository npcAffinityEventRepository;
	@Autowired private NpcAffinityStateRepository npcAffinityStateRepository;

	@Autowired
	private CharacterRepository characterRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	private Character character;
	private Photo photo;
	private String accessToken;

	@BeforeEach
	void setUp() {
		deleteTestData();

		User user = createUser("recognition@example.com", "eden");
		character = createCharacter(user, "에덴");
		World world = worldRepository.save(World.create(character, 12345L));
		Region region = regionRepository.save(Region.create(world, RegionType.FLOWER_FIELD));
		Plant plant = plantRepository.save(Plant.create(region, character, SeedType.FLOWER, true));
		plant.updateStage(PlantStage.BLOOMED);
		plantRepository.save(plant);
		photo = createPhoto(character, plant);
		accessToken = jwtTokenProvider.generateAccessToken(user);
	}

	@AfterEach
	void cleanUp() {
		deleteTestData();
	}

	@Test
	void recognitionSucceeds() throws Exception {
		performRecognize(photo.getId(), accessToken)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.photoId").value(photo.getId()));

		assertEquals(1, recognitionRepository.count());
		assertEquals(1, villageThemeSnapshotRepository.count());
	}

	@Test
	void generalPhotoWithoutPlantCanBeRecognizedAndRecordedAsVillageMemory() throws Exception {
		Photo generalPhoto = createPhoto(character, null);

		performRecognize(generalPhoto.getId(), accessToken)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.photoId").value(generalPhoto.getId()))
				.andExpect(jsonPath("$.recognizedObject").value("FLOWER"))
				.andExpect(jsonPath("$.category").value("NATURE"))
				.andExpect(jsonPath("$.fallback").value(false));

		assertEquals(1, villageMemoryRepository.count());
		assertEquals(1, villageThemeSnapshotRepository.count());
	}

	@Test
	void mockRecognitionMapsCatPhotoToAnimal() throws Exception {
		assertMockRecognition("my-cat.jpg", "CAT", "ANIMAL", true, false);
	}

	@Test
	void mockRecognitionMapsFlowerPhotoToNature() throws Exception {
		assertMockRecognition("rose-flower.jpg", "FLOWER", "NATURE", true, false);
	}

	@Test
	void mockRecognitionMapsBreadPhotoToFood() throws Exception {
		assertMockRecognition("warm-bread.jpg", "BREAD", "FOOD", true, false);
	}

	@Test
	void mockRecognitionMapsRiverPhotoToWater() throws Exception {
		assertMockRecognition("river-memory.jpg", "RIVER", "WATER", true, false);
	}

	@Test
	void mockRecognitionMapsParkPathPhotoToWalk() throws Exception {
		assertMockRecognition("park-path.jpg", "PATH", "WALK", true, false);
	}

	@Test
	void mockRecognitionMapsStudyBookPhotoToStudy() throws Exception {
		assertMockRecognition("study-book.jpg", "STUDY", "STUDY", true, false);
	}

	@Test
	void mockRecognitionMapsLectureNotePhotoToStudy() throws Exception {
		assertMockRecognition("lecture-note.jpg", "LECTURE", "STUDY", true, false);
	}

	@Test
	void mockRecognitionMapsLibraryReadingPhotoToStudy() throws Exception {
		assertMockRecognition("library-reading.jpg", "READING", "STUDY", true, false);
	}

	@Test
	void mockRecognitionMapsCodingLaptopPhotoToWork() throws Exception {
		assertMockRecognition("coding-laptop.jpg", "CODING", "WORK", true, false);
	}

	@Test
	void mockRecognitionMapsProgrammingProjectPhotoToWork() throws Exception {
		assertMockRecognition("programming-project.jpg", "PROGRAMMING", "WORK", true, false);
	}

	@Test
	void mockRecognitionMapsNotebookComputerPhotoToWork() throws Exception {
		assertMockRecognition("notebook-computer.jpg", "COMPUTER", "WORK", true, false);
	}

	@Test
	void unknownPhotoReturnsFallbackAndRecordsUnknownVillageMemory() throws Exception {
		Photo unknownPhoto = createPhoto(character, null, "IMG_1234.HEIC");

		performRecognize(unknownPhoto.getId(), accessToken)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recognizedObject").value("UNKNOWN"))
				.andExpect(jsonPath("$.category").value("UNKNOWN"))
				.andExpect(jsonPath("$.confidence").value(0))
				.andExpect(jsonPath("$.recognized").value(false))
				.andExpect(jsonPath("$.fallback").value(true));

		assertEquals(1, villageMemoryRepository.count());
	}

	@Test
	void duplicateRecognitionIsReused() throws Exception {
		String firstResponse = performRecognize(photo.getId(), accessToken)
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		String secondResponse = performRecognize(photo.getId(), accessToken)
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertEquals(firstResponse, secondResponse);
		assertEquals(1, recognitionRepository.count());
	}

	@Test
	void missingPhotoReturnsNotFound() throws Exception {
		performRecognize(999999L, accessToken)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("사진을 찾을 수 없습니다."));
	}

	@Test
	void anotherUsersPhotoCannotBeRecognized() throws Exception {
		User anotherUser = createUser("another-recognition@example.com", "another");
		Character anotherCharacter = createCharacter(anotherUser, "다른이");
		Photo anotherPhoto = createPhoto(anotherCharacter, null);

		performRecognize(anotherPhoto.getId(), accessToken)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("다른 사용자의 사진은 인식할 수 없습니다."));
	}

	@Test
	void recognitionRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/photos/{photoId}/recognize", photo.getId()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void multipartRecognitionUsesValidatedImageAndPersistsOnce() throws Exception {
		Photo catPhoto = createPhoto(character, null, "cat.jpg");
		MockMultipartFile image = new MockMultipartFile(
				"file", "cat.jpg", "image/jpeg", jpegBytes());

		mockMvc.perform(multipart("/api/photos/{photoId}/recognize-with-image", catPhoto.getId())
				.file(image)
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.photoId").value(catPhoto.getId()))
				.andExpect(jsonPath("$.recognizedObject").value("CAT"))
				.andExpect(jsonPath("$.category").value("ANIMAL"));

		assertEquals(1, recognitionRepository.count());
	}

	@Test
	void multipartRecognitionRejectsCorruptImageWithoutPersistence() throws Exception {
		MockMultipartFile corrupt = new MockMultipartFile(
				"file", "cat.jpg", "image/jpeg",
				new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2});

		mockMvc.perform(multipart("/api/photos/{photoId}/recognize-with-image", photo.getId())
				.file(corrupt)
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("이미지를 읽을 수 없습니다."));

		assertEquals(0, recognitionRepository.count());
	}

	@Test
	void mockRecognitionReturnsFlowerWithNinetyFiveConfidence() throws Exception {
		performRecognize(photo.getId(), accessToken)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recognizedObject").value("FLOWER"))
				.andExpect(jsonPath("$.category").value("NATURE"))
				.andExpect(jsonPath("$.confidence").value(95))
				.andExpect(jsonPath("$.recognized").value(true))
				.andExpect(jsonPath("$.fallback").value(false));
	}

	@Test
	void myRecognitionsCanBeRetrieved() throws Exception {
		performRecognize(photo.getId(), accessToken).andExpect(status().isOk());

		mockMvc.perform(get("/api/photos/recognitions")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].photoId").value(photo.getId()))
				.andExpect(jsonPath("$[0].recognizedObject").value("FLOWER"))
				.andExpect(jsonPath("$[0].category").value("NATURE"));
	}

	private void assertMockRecognition(
			String originalFileName,
			String expectedObject,
			String expectedCategory,
			boolean expectedRecognized,
			boolean expectedFallback) throws Exception {
		Photo target = createPhoto(character, null, originalFileName);

		performRecognize(target.getId(), accessToken)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recognizedObject").value(expectedObject))
				.andExpect(jsonPath("$.category").value(expectedCategory))
				.andExpect(jsonPath("$.recognized").value(expectedRecognized))
				.andExpect(jsonPath("$.fallback").value(expectedFallback));
	}

	private org.springframework.test.web.servlet.ResultActions performRecognize(Long photoId, String token)
			throws Exception {
		return mockMvc.perform(post("/api/photos/{photoId}/recognize", photoId)
				.header("Authorization", "Bearer " + token));
	}

	private User createUser(String email, String nickname) {
		return userRepository.save(new User(email, passwordEncoder.encode("password123"), nickname));
	}

	private Character createCharacter(User user, String name) {
		return characterRepository.save(Character.create(
				user,
				name,
				CharacterGender.NONE,
				HairStyle.PIXEL_CUT,
				"brown",
				Outfit.ROBE,
				CharacterJob.WIZARD));
	}

	private Photo createPhoto(Character owner, Plant plant) {
		return createPhoto(owner, plant, "flower.jpg");
	}

	private Photo createPhoto(Character owner, Plant plant, String originalFileName) {
		String storedFileName = UUID.randomUUID() + ".jpg";
		return photoRepository.save(Photo.create(
				owner,
				plant,
				originalFileName,
				storedFileName,
				"image/jpeg",
				10,
				"/uploads/photos/" + storedFileName));
	}

	private byte[] jpegBytes() {
		try {
			BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
			image.setRGB(0, 0, 0xff446688);
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			if (!ImageIO.write(image, "jpg", output)) {
				throw new AssertionError("JPEG test writer is unavailable.");
			}
			return output.toByteArray();
		} catch (java.io.IOException exception) {
			throw new AssertionError(exception);
		}
	}

	private void deleteTestData() {
		worldPlacedObjectRepository.deleteAllInBatch();
		worldChangeRepository.deleteAllInBatch();
		villageHistoryRepository.deleteAll();
		villageChangeRepository.deleteAll();
		villageThemeSnapshotRepository.deleteAll();
		villageMemoryRepository.deleteAll();
		evolutionHistoryRepository.deleteAll();
		worldDecorationRepository.deleteAll();
		worldEvolutionRepository.deleteAll();
		recognitionRepository.deleteAll();
		npcQuestEventRepository.deleteAll();
		npcQuestStateRepository.deleteAll();
		npcAffinityEventRepository.deleteAll();
		npcAffinityStateRepository.deleteAll();
		photoRepository.deleteAll();
		plantRepository.deleteAll();
		regionRepository.deleteAll();
		worldRepository.deleteAll();
		characterRepository.deleteAll();
		userRepository.deleteAll();
	}
}
