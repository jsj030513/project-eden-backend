package com.projecteden.resonance;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.projecteden.ai.domain.Recognition;
import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.ai.repository.RecognitionRepository;
import com.projecteden.auth.jwt.JwtTokenProvider;
import com.projecteden.character.domain.Character;
import com.projecteden.character.domain.CharacterGender;
import com.projecteden.character.domain.CharacterJob;
import com.projecteden.character.domain.HairStyle;
import com.projecteden.character.domain.Outfit;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.house.domain.House;
import com.projecteden.house.repository.HouseRepository;
import com.projecteden.inventory.domain.Inventory;
import com.projecteden.inventory.repository.InventoryRepository;
import com.projecteden.photo.domain.Photo;
import com.projecteden.photo.repository.PhotoRepository;
import com.projecteden.plant.repository.PlantRepository;
import com.projecteden.region.repository.RegionRepository;
import com.projecteden.resonance.domain.Resonance;
import com.projecteden.resonance.domain.ResonanceRewardType;
import com.projecteden.resonance.repository.ResonanceRepository;
import com.projecteden.seed.domain.Seed;
import com.projecteden.seed.domain.SeedType;
import com.projecteden.seed.repository.SeedRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;
import com.projecteden.collection.repository.CollectionRepository;
import com.projecteden.statistics.repository.CharacterStatisticsRepository;
import com.projecteden.achievement.repository.UserAchievementRepository;
import com.projecteden.title.repository.UserTitleRepository;
import com.projecteden.evolution.repository.WorldEvolutionRepository;
import com.projecteden.evolution.repository.WorldDecorationRepository;
import com.projecteden.evolution.repository.EvolutionHistoryRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResonanceIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ResonanceRepository resonanceRepository;

	@Autowired private EvolutionHistoryRepository evolutionHistoryRepository;
	@Autowired private WorldDecorationRepository worldDecorationRepository;
	@Autowired private WorldEvolutionRepository worldEvolutionRepository;

	@Autowired
	private UserTitleRepository userTitleRepository;

	@Autowired
	private UserAchievementRepository userAchievementRepository;

	@Autowired
	private CharacterStatisticsRepository characterStatisticsRepository;

	@Autowired
	private CollectionRepository collectionRepository;

	@Autowired
	private RecognitionRepository recognitionRepository;

	@Autowired
	private PhotoRepository photoRepository;

	@Autowired
	private PlantRepository plantRepository;

	@Autowired
	private SeedRepository seedRepository;

	@Autowired
	private InventoryRepository inventoryRepository;

	@Autowired
	private HouseRepository houseRepository;

	@Autowired
	private RegionRepository regionRepository;

	@Autowired
	private WorldRepository worldRepository;

	@Autowired
	private CharacterRepository characterRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	private Character character;
	private World world;
	private Inventory inventory;
	private Recognition flowerRecognition;
	private String accessToken;

	@BeforeEach
	void setUp() {
		deleteTestData();

		User user = createUser("resonance@example.com", "eden");
		character = createCharacter(user, "에덴");
		world = worldRepository.save(World.create(character, 12345L));
		House house = houseRepository.save(House.create(world));
		inventory = inventoryRepository.save(Inventory.create(house));
		seedRepository.save(Seed.create(inventory, SeedType.FLOWER, 5));
		flowerRecognition = createRecognition(character, RecognizedObject.FLOWER, true);
		accessToken = jwtTokenProvider.generateAccessToken(user);
	}

	@AfterEach
	void cleanUp() {
		deleteTestData();
	}

	@Test
	void flowerRecognitionCreatesResonance() throws Exception {
		performCreate(flowerRecognition.getId(), accessToken)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recognizedObject").value("FLOWER"))
				.andExpect(jsonPath("$.rewardType").value("SEED"))
				.andExpect(jsonPath("$.rewardSeedType").value("FLOWER"))
				.andExpect(jsonPath("$.rewardSeedQuantity").value(1))
				.andExpect(jsonPath("$.rewardGold").value(0));
	}

	@Test
	void flowerResonanceAddsOneFlowerSeed() throws Exception {
		int quantityBefore = findSeed(SeedType.FLOWER).getQuantity();

		performCreate(flowerRecognition.getId(), accessToken).andExpect(status().isOk());

		assertEquals(quantityBefore + 1, findSeed(SeedType.FLOWER).getQuantity());
	}

	@Test
	void resonanceRecordIsSaved() throws Exception {
		performCreate(flowerRecognition.getId(), accessToken).andExpect(status().isOk());

		assertEquals(1, resonanceRepository.count());
	}

	@Test
	void sameRecognitionCannotResonateTwice() throws Exception {
		performCreate(flowerRecognition.getId(), accessToken).andExpect(status().isOk());

		performCreate(flowerRecognition.getId(), accessToken)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("이미 공명이 발생한 인식 결과입니다."));
	}

	@Test
	void sameObjectCannotRewardTwiceOnSameDate() throws Exception {
		Recognition anotherFlower = createRecognition(character, RecognizedObject.FLOWER, true);
		performCreate(flowerRecognition.getId(), accessToken).andExpect(status().isOk());

		performCreate(anotherFlower.getId(), accessToken)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("오늘 이미 같은 대상의 공명 보상을 받았습니다."));
	}

	@Test
	void moreThanTenRewardsCannotBeClaimedPerDay() throws Exception {
		for (int index = 0; index < 10; index++) {
			Recognition recognition = createRecognition(character, RecognizedObject.FLOWER, true);
			resonanceRepository.save(Resonance.create(
					character,
					recognition,
					RecognizedObject.FLOWER,
					ResonanceRewardType.SEED,
					SeedType.FLOWER,
					1,
					0,
					LocalDate.now()));
		}
		Recognition unknown = createRecognition(character, RecognizedObject.UNKNOWN, true);

		performCreate(unknown.getId(), accessToken)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("오늘의 공명 보상 횟수를 모두 사용했습니다."));
	}

	@Test
	void anotherUsersRecognitionCannotCreateResonance() throws Exception {
		User anotherUser = createUser("another-resonance@example.com", "another");
		Character anotherCharacter = createCharacter(anotherUser, "다른이");
		Recognition anotherRecognition = createRecognition(
				anotherCharacter, RecognizedObject.FLOWER, true);

		performCreate(anotherRecognition.getId(), accessToken)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("다른 사용자의 인식 결과로 공명할 수 없습니다."));
	}

	@Test
	void unrecognizedResultCannotCreateResonance() throws Exception {
		Recognition failedRecognition = createRecognition(character, RecognizedObject.UNKNOWN, false);

		performCreate(failedRecognition.getId(), accessToken)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("인식에 실패한 결과로는 공명할 수 없습니다."));
	}

	@Test
	void unknownRecognitionAddsFiveGold() throws Exception {
		Recognition unknown = createRecognition(character, RecognizedObject.UNKNOWN, true);
		int goldBefore = world.getGold();

		performCreate(unknown.getId(), accessToken)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rewardType").value("NONE"))
				.andExpect(jsonPath("$.rewardSeedType").doesNotExist())
				.andExpect(jsonPath("$.rewardGold").value(5));

		assertEquals(goldBefore + 5, worldRepository.findById(world.getId()).orElseThrow().getGold());
	}

	@Test
	void myResonancesCanBeRetrieved() throws Exception {
		performCreate(flowerRecognition.getId(), accessToken).andExpect(status().isOk());

		mockMvc.perform(get("/api/resonances/me")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].recognizedObject").value("FLOWER"));
	}

	@Test
	void resonanceRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/resonances")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"recognitionId\":" + flowerRecognition.getId() + "}"))
				.andExpect(status().isUnauthorized());
	}

	private org.springframework.test.web.servlet.ResultActions performCreate(
			Long recognitionId, String token) throws Exception {
		return mockMvc.perform(post("/api/resonances")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"recognitionId\":" + recognitionId + "}"));
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

	private Recognition createRecognition(
			Character owner, RecognizedObject recognizedObject, boolean recognized) {
		String storedFileName = UUID.randomUUID() + ".jpg";
		Photo photo = photoRepository.save(Photo.create(
				owner,
				null,
				"photo.jpg",
				storedFileName,
				"image/jpeg",
				10,
				"/uploads/photos/" + storedFileName));
		return recognitionRepository.save(Recognition.create(photo, recognizedObject, 95, recognized));
	}

	private Seed findSeed(SeedType seedType) {
		return seedRepository.findByInventoryIdAndSeedType(inventory.getId(), seedType).orElseThrow();
	}

	private void deleteTestData() {
		evolutionHistoryRepository.deleteAll();
		worldDecorationRepository.deleteAll();
		worldEvolutionRepository.deleteAll();
		userTitleRepository.deleteAll();
		userAchievementRepository.deleteAll();
		characterStatisticsRepository.deleteAll();
		collectionRepository.deleteAll();
		resonanceRepository.deleteAll();
		recognitionRepository.deleteAll();
		photoRepository.deleteAll();
		plantRepository.deleteAll();
		seedRepository.deleteAll();
		inventoryRepository.deleteAll();
		houseRepository.deleteAll();
		regionRepository.deleteAll();
		worldRepository.deleteAll();
		characterRepository.deleteAll();
		userRepository.deleteAll();
	}
}
