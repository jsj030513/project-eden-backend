package com.projecteden.harvest;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
import com.projecteden.plant.domain.Plant;
import com.projecteden.plant.domain.PlantStage;
import com.projecteden.plant.repository.PlantRepository;
import com.projecteden.region.domain.Region;
import com.projecteden.region.domain.RegionType;
import com.projecteden.region.repository.RegionRepository;
import com.projecteden.region.service.RegionService;
import com.projecteden.seed.domain.Seed;
import com.projecteden.seed.domain.SeedType;
import com.projecteden.seed.repository.SeedRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(HarvestIntegrationTests.ClockTestConfig.class)
class HarvestIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MutableClock mutableClock;

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

	private User user;
	private Character character;
	private World world;
	private Plant plant;
	private String accessToken;

	@BeforeEach
	void setUp() throws Exception {
		deleteTestData();
		mutableClock.setInstant(Instant.now());

		user = userRepository.save(new User(
				"harvest@example.com",
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
		world = worldRepository.save(World.create(character, 12345L));
		regionService.createDefaultRegions(world.getId());
		houseRepository.save(House.create(world));
		accessToken = jwtTokenProvider.generateAccessToken(user);

		mockMvc.perform(post("/api/inventories")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isCreated());
		performPlant().andExpect(status().isOk());
		plant = plantRepository.findByCharacterId(character.getId()).getFirst();
	}

	@AfterEach
	void cleanUp() {
		deleteTestData();
	}

	@Test
	void bloomedPlantCanBeHarvested() throws Exception {
		bloom(plant);

		performHarvest(plant.getId())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.plantId").value(plant.getId()))
				.andExpect(jsonPath("$.seedType").value("FLOWER"))
				.andExpect(jsonPath("$.earnedGold").value(10))
				.andExpect(jsonPath("$.earnedSeedType").value("FLOWER"))
				.andExpect(jsonPath("$.earnedSeedQuantity").value(1))
				.andExpect(jsonPath("$.message").value("FLOWER를 수확했습니다."));
	}

	@Test
	void seedStagePlantCannotBeHarvested() throws Exception {
		performHarvest(plant.getId())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("개화한 식물만 수확할 수 있습니다."));
	}

	@Test
	void anotherUsersPlantCannotBeHarvested() throws Exception {
		User anotherUser = userRepository.save(new User(
				"another@example.com",
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
		Region flowerField = regionRepository
				.findByWorldIdAndRegionType(world.getId(), RegionType.FLOWER_FIELD)
				.orElseThrow();
		Plant anotherPlant = plantRepository.save(Plant.create(
				flowerField, anotherCharacter, SeedType.FLOWER, false));
		bloom(anotherPlant);

		performHarvest(anotherPlant.getId())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("다른 사용자의 식물은 수확할 수 없습니다."));
	}

	@Test
	void harvestingIncreasesGold() throws Exception {
		bloom(plant);
		int goldBefore = world.getGold();

		performHarvest(plant.getId()).andExpect(status().isOk());

		assertEquals(goldBefore + 10, worldRepository.findById(world.getId()).orElseThrow().getGold());
	}

	@Test
	void harvestingIncreasesSeedQuantity() throws Exception {
		bloom(plant);
		Seed flowerSeed = seedRepository.findAll().getFirst();
		int quantityBefore = flowerSeed.getQuantity();

		performHarvest(plant.getId()).andExpect(status().isOk());

		Seed rewardedSeed = seedRepository.findById(flowerSeed.getId()).orElseThrow();
		assertEquals(quantityBefore + 1, rewardedSeed.getQuantity());
	}

	@Test
	void harvestedPlantCannotBeHarvestedAgain() throws Exception {
		bloom(plant);
		performHarvest(plant.getId()).andExpect(status().isOk());

		performHarvest(plant.getId()).andExpect(status().isNotFound());
	}

	@Test
	void getHarvestablePlantsSucceeds() throws Exception {
		mutableClock.setLocalDateTime(plant.getPlantedAt().plusSeconds(60));

		mockMvc.perform(get("/api/plants/harvestable")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].plantStage").value("BLOOMED"));
	}

	@Test
	void getHarvestablePlantsFailsWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/plants/harvestable"))
				.andExpect(status().isUnauthorized());
	}

	private void bloom(Plant target) {
		target.updateStage(PlantStage.BLOOMED);
		plantRepository.save(target);
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

	private org.springframework.test.web.servlet.ResultActions performHarvest(Long plantId) throws Exception {
		return mockMvc.perform(post("/api/plants/{plantId}/harvest", plantId)
				.header("Authorization", "Bearer " + accessToken));
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

	@TestConfiguration
	static class ClockTestConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(Instant.now(), ZoneId.systemDefault());
		}
	}

	static class MutableClock extends Clock {

		private Instant instant;
		private final ZoneId zone;

		MutableClock(Instant instant, ZoneId zone) {
			this.instant = instant;
			this.zone = zone;
		}

		void setInstant(Instant instant) {
			this.instant = instant;
		}

		void setLocalDateTime(LocalDateTime dateTime) {
			this.instant = dateTime.atZone(zone).toInstant();
		}

		@Override
		public ZoneId getZone() {
			return zone;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return new MutableClock(instant, zone);
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
