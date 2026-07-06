package com.projecteden.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
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
@Import(PlantGrowthIntegrationTests.ClockTestConfig.class)
class PlantGrowthIntegrationTests {

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

	private String accessToken;

	@BeforeEach
	void setUp() throws Exception {
		deleteTestData();
		mutableClock.setInstant(Instant.now());

		User user = userRepository.save(new User(
				"growth@example.com",
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
	void resonancePlantIsSeedImmediately() throws Exception {
		Plant plant = createResonancePlant();
		assertStageAfter(plant, Duration.ZERO, PlantStage.SEED);
	}

	@Test
	void resonancePlantSproutsAfterTenSeconds() throws Exception {
		Plant plant = createResonancePlant();
		assertStageAfter(plant, Duration.ofSeconds(10), PlantStage.SPROUT);
	}

	@Test
	void resonancePlantGrowsAfterThirtySeconds() throws Exception {
		Plant plant = createResonancePlant();
		assertStageAfter(plant, Duration.ofSeconds(30), PlantStage.GROWING);
	}

	@Test
	void resonancePlantBloomsAfterSixtySeconds() throws Exception {
		Plant plant = createResonancePlant();
		assertStageAfter(plant, Duration.ofSeconds(60), PlantStage.BLOOMED);
	}

	@Test
	void normalPlantIsSeedImmediately() throws Exception {
		Plant plant = createNormalPlant();
		assertStageAfter(plant, Duration.ZERO, PlantStage.SEED);
	}

	@Test
	void normalPlantSproutsAfterOneDay() throws Exception {
		Plant plant = createNormalPlant();
		assertStageAfter(plant, Duration.ofDays(1), PlantStage.SPROUT);
	}

	@Test
	void normalPlantGrowsAfterTwoDays() throws Exception {
		Plant plant = createNormalPlant();
		assertStageAfter(plant, Duration.ofDays(2), PlantStage.GROWING);
	}

	@Test
	void normalPlantBloomsAfterThreeDays() throws Exception {
		Plant plant = createNormalPlant();
		assertStageAfter(plant, Duration.ofDays(3), PlantStage.BLOOMED);
	}

	private Plant createResonancePlant() throws Exception {
		performPlant();
		return plantRepository.findAll().getFirst();
	}

	private Plant createNormalPlant() throws Exception {
		performPlant();
		performPlant();
		return plantRepository.findAll().stream()
				.filter(plant -> !plant.isResonanceBoosted())
				.findFirst()
				.orElseThrow();
	}

	private void assertStageAfter(Plant plant, Duration elapsed, PlantStage expected) throws Exception {
		LocalDateTime checkedAt = plant.getPlantedAt().plus(elapsed);
		mutableClock.setLocalDateTime(checkedAt);

		mockMvc.perform(get("/api/plants/me")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk());

		Plant refreshed = plantRepository.findById(plant.getId()).orElseThrow();
		assertEquals(expected, refreshed.getPlantStage());
		assertEquals(checkedAt, refreshed.getLastGrowthCheckedAt());
	}

	private void performPlant() throws Exception {
		mockMvc.perform(post("/api/seeds/plant")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "seedType": "FLOWER"
						}
						"""))
				.andExpect(status().isOk());
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
