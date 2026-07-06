package com.projecteden.plant;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.projecteden.auth.jwt.JwtTokenProvider;
import com.projecteden.character.domain.Character;
import com.projecteden.character.domain.CharacterGender;
import com.projecteden.character.domain.CharacterJob;
import com.projecteden.character.domain.HairStyle;
import com.projecteden.character.domain.Outfit;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.house.domain.House;
import com.projecteden.house.repository.HouseRepository;
import com.projecteden.inventory.repository.InventoryRepository;
import com.projecteden.plant.repository.PlantRepository;
import com.projecteden.region.repository.RegionRepository;
import com.projecteden.region.service.RegionService;
import com.projecteden.seed.repository.SeedRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlantIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

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
	private RegionService regionService;

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

	private String accessToken;

	@BeforeEach
	void setUp() throws Exception {
		deleteTestData();

		User user = userRepository.save(new User(
				"plant@example.com",
				passwordEncoder.encode("password123"),
				"eden"));
		Character character = characterRepository.save(Character.create(
				user,
				"에덴",
				CharacterGender.NONE,
				HairStyle.PIXEL_CUT,
				"brown",
				Outfit.ROBE,
				CharacterJob.WIZARD));
		World world = worldRepository.save(World.create(character, 12345L));
		regionService.createDefaultRegions(world.getId());
		houseRepository.save(House.create(world));
		accessToken = jwtTokenProvider.generateAccessToken(user);

		mockMvc.perform(post("/api/inventories")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isCreated());
	}

	@AfterEach
	void cleanUp() {
		deleteTestData();
	}

	@Test
	void plantingSeedCreatesPlant() throws Exception {
		performPlant().andExpect(status().isOk());
		assertEquals(1, plantRepository.count());
	}

	@Test
	void firstPlantIsResonanceBoosted() throws Exception {
		performPlant()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.resonanceBoosted").value(true));
	}

	@Test
	void secondPlantIsNotResonanceBoosted() throws Exception {
		performPlant().andExpect(status().isOk());
		performPlant()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.resonanceBoosted").value(false));
	}

	@Test
	void plantStageDefaultsToSeed() throws Exception {
		performPlant()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.plantStage").value("SEED"));
	}

	@Test
	void plantIsCreatedInFlowerField() throws Exception {
		performPlant().andExpect(status().isOk());

		mockMvc.perform(get("/api/plants/me")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].regionType").value("FLOWER_FIELD"));
	}

	@Test
	void getMyPlantsSucceeds() throws Exception {
		performPlant().andExpect(status().isOk());

		mockMvc.perform(get("/api/plants/me")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].seedType").value("FLOWER"))
				.andExpect(jsonPath("$[0].plantedAt").isNotEmpty());
	}

	@Test
	void getMyPlantsFailsWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/plants/me"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void plantingStillDecreasesSeedQuantity() throws Exception {
		performPlant()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.remaining").value(4));
		assertEquals(4, seedRepository.findAll().getFirst().getQuantity());
	}

	private org.springframework.test.web.servlet.ResultActions performPlant() throws Exception {
		return mockMvc.perform(post("/api/seeds/plant")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "seedType": "FLOWER"
						}
						"""));
	}

	private void deleteTestData() {
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
