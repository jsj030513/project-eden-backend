package com.projecteden.daily;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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
import com.projecteden.daily.repository.DailyMissionRepository;
import com.projecteden.house.domain.House;
import com.projecteden.house.repository.HouseRepository;
import com.projecteden.inventory.repository.InventoryRepository;
import com.projecteden.plant.domain.Plant;
import com.projecteden.plant.domain.PlantStage;
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
class DailyIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DailyMissionRepository dailyMissionRepository;

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

	private Character character;
	private String accessToken;

	@BeforeEach
	void setUp() throws Exception {
		deleteTestData();

		User user = userRepository.save(new User(
				"daily@example.com",
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
	void todayMissionIsCreatedAutomatically() throws Exception {
		performGetDaily().andExpect(status().isOk());

		assertEquals(1, dailyMissionRepository.count());
		assertEquals(LocalDate.now(), dailyMissionRepository.findAll().getFirst().getMissionDate());
	}

	@Test
	void plantingSeedCompletesPlantMission() throws Exception {
		performPlant().andExpect(status().isOk());

		performGetDaily()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.plantCompleted").value(true))
				.andExpect(jsonPath("$.harvestCompleted").value(false));
	}

	@Test
	void harvestingCompletesHarvestMission() throws Exception {
		performPlant().andExpect(status().isOk());
		Plant plant = plantRepository.findByCharacterId(character.getId()).getFirst();
		plant.updateStage(PlantStage.BLOOMED);
		plantRepository.save(plant);

		mockMvc.perform(post("/api/plants/{plantId}/harvest", plant.getId())
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk());

		performGetDaily()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.plantCompleted").value(true))
				.andExpect(jsonPath("$.harvestCompleted").value(true));
	}

	@Test
	void getTodayMissionSucceeds() throws Exception {
		performGetDaily()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.missionDate").value(LocalDate.now().toString()))
				.andExpect(jsonPath("$.plantCompleted").value(false))
				.andExpect(jsonPath("$.harvestCompleted").value(false))
				.andExpect(jsonPath("$.photoCompleted").value(false))
				.andExpect(jsonPath("$.rewardClaimed").value(false));
	}

	@Test
	void getTodayMissionFailsWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/daily"))
				.andExpect(status().isUnauthorized());
	}

	private org.springframework.test.web.servlet.ResultActions performGetDaily() throws Exception {
		return mockMvc.perform(get("/api/daily")
				.header("Authorization", "Bearer " + accessToken));
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
		dailyMissionRepository.deleteAll();
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
