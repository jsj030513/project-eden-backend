package com.projecteden.photo;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.projecteden.auth.jwt.JwtTokenProvider;
import com.projecteden.character.domain.Character;
import com.projecteden.character.domain.CharacterGender;
import com.projecteden.character.domain.CharacterJob;
import com.projecteden.character.domain.HairStyle;
import com.projecteden.character.domain.Outfit;
import com.projecteden.character.repository.CharacterRepository;
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
import com.projecteden.world.repository.WorldRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhotoIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PhotoRepository photoRepository;

	@Autowired
	private PlantRepository plantRepository;

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
	private Region flowerField;
	private Plant plant;
	private String accessToken;

	@BeforeEach
	void setUp() {
		deleteTestData();

		User user = userRepository.save(new User(
				"photo@example.com",
				passwordEncoder.encode("password123"),
				"eden"));
		character = characterRepository.save(Character.create(
				user,
				"에덴",
				CharacterGender.NONE,
				HairStyle.PIXEL_CUT,
				"brown",
				Outfit.ROBE,
				CharacterJob.WIZARD));
		World world = worldRepository.save(World.create(character, 12345L));
		flowerField = regionRepository.save(Region.create(world, RegionType.FLOWER_FIELD));
		plant = plantRepository.save(Plant.create(flowerField, character, SeedType.FLOWER, true));
		accessToken = jwtTokenProvider.generateAccessToken(user);
	}

	@AfterEach
	void cleanUp() {
		deleteTestData();
	}

	@Test
	void bloomedPlantPhotoCanBeUploaded() throws Exception {
		bloom(plant);

		performUpload(plant.getId(), accessToken)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.plantId").value(plant.getId()))
				.andExpect(jsonPath("$.imageUrl").value(org.hamcrest.Matchers.matchesPattern(
						"/uploads/photos/[0-9a-f\\-]+\\.jpg")))
				.andExpect(jsonPath("$.uploadedAt").exists());
	}

	@Test
	void nonBloomedPlantPhotoCannotBeUploaded() throws Exception {
		performUpload(plant.getId(), accessToken)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("개화한 식물에만 사진을 업로드할 수 있습니다."));
	}

	@Test
	void anotherUsersPlantPhotoCannotBeUploaded() throws Exception {
		User anotherUser = userRepository.save(new User(
				"another-photo@example.com",
				passwordEncoder.encode("password123"),
				"another"));
		Character anotherCharacter = characterRepository.save(Character.create(
				anotherUser,
				"다른이",
				CharacterGender.NONE,
				HairStyle.SHORT,
				"black",
				Outfit.BASIC,
				CharacterJob.BEGINNER));
		Plant anotherPlant = plantRepository.save(
				Plant.create(flowerField, anotherCharacter, SeedType.FLOWER, false));
		bloom(anotherPlant);

		performUpload(anotherPlant.getId(), accessToken)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("다른 사용자의 식물에는 사진을 업로드할 수 없습니다."));
	}

	@Test
	void myPhotosCanBeRetrieved() throws Exception {
		bloom(plant);
		performUpload(plant.getId(), accessToken).andExpect(status().isCreated());

		mockMvc.perform(get("/api/photos/me")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").isNumber())
				.andExpect(jsonPath("$[0].imageUrl").value(org.hamcrest.Matchers.startsWith(
						"/uploads/photos/")))
				.andExpect(jsonPath("$[0].uploadedAt").exists());
	}

	@Test
	void photoCannotBeUploadedWithoutAuthentication() throws Exception {
		bloom(plant);

		mockMvc.perform(multipart("/api/photos")
				.file(photoFile())
				.param("plantId", plant.getId().toString()))
				.andExpect(status().isUnauthorized());
	}

	private org.springframework.test.web.servlet.ResultActions performUpload(Long plantId, String token)
			throws Exception {
		return mockMvc.perform(multipart("/api/photos")
				.file(photoFile())
				.param("plantId", plantId.toString())
				.header("Authorization", "Bearer " + token));
	}

	private MockMultipartFile photoFile() {
		return new MockMultipartFile("file", "flower.jpg", "image/jpeg", "mock-image".getBytes());
	}

	private void bloom(Plant target) {
		target.updateStage(PlantStage.BLOOMED);
		plantRepository.save(target);
	}

	private void deleteTestData() {
		photoRepository.deleteAll();
		plantRepository.deleteAll();
		regionRepository.deleteAll();
		worldRepository.deleteAll();
		characterRepository.deleteAll();
		userRepository.deleteAll();
	}
}
