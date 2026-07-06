package com.projecteden.daily;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import com.projecteden.daily.domain.DailyMission;
import com.projecteden.daily.repository.DailyMissionRepository;
import com.projecteden.daily.service.DailyService;
import com.projecteden.house.domain.House;
import com.projecteden.house.repository.HouseRepository;
import com.projecteden.inventory.domain.Inventory;
import com.projecteden.inventory.repository.InventoryRepository;
import com.projecteden.region.repository.RegionRepository;
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
class DailyRewardIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DailyService dailyService;

	@Autowired
	private DailyMissionRepository dailyMissionRepository;

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
	private String accessToken;

	@BeforeEach
	void setUp() throws Exception {
		deleteTestData();

		User user = userRepository.save(new User(
				"daily-reward@example.com",
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
		House house = houseRepository.save(House.create(world));
		inventory = inventoryRepository.save(Inventory.create(house));
		seedRepository.save(Seed.create(inventory, SeedType.FLOWER, 5));
		accessToken = jwtTokenProvider.generateAccessToken(user);
	}

	@AfterEach
	void cleanUp() {
		deleteTestData();
	}

	@Test
	void completedMissionRewardCanBeClaimed() throws Exception {
		completeBothMissions();

		performClaimReward()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.earnedGold").value(50))
				.andExpect(jsonPath("$.earnedSeedType").value("FLOWER"))
				.andExpect(jsonPath("$.earnedSeedQuantity").value(2))
				.andExpect(jsonPath("$.rewardClaimed").value(true))
				.andExpect(jsonPath("$.message").value("일일 미션 보상을 수령했습니다."));
	}

	@Test
	void claimingRewardIncreasesGoldByFifty() throws Exception {
		completeBothMissions();
		int goldBefore = world.getGold();

		performClaimReward().andExpect(status().isOk());

		assertEquals(goldBefore + 50, worldRepository.findById(world.getId()).orElseThrow().getGold());
	}

	@Test
	void claimingRewardAddsTwoFlowerSeeds() throws Exception {
		completeBothMissions();
		int quantityBefore = findFlowerSeed().getQuantity();

		performClaimReward().andExpect(status().isOk());

		assertEquals(quantityBefore + 2, findFlowerSeed().getQuantity());
	}

	@Test
	void claimingRewardMarksMissionAsClaimed() throws Exception {
		completeBothMissions();

		performClaimReward().andExpect(status().isOk());

		DailyMission mission = dailyMissionRepository.findAll().getFirst();
		assertTrue(mission.isRewardClaimed());
	}

	@Test
	void rewardCannotBeClaimedWhenPlantMissionIsIncomplete() throws Exception {
		dailyService.completeHarvestMission(character.getId());

		performClaimReward().andExpect(status().isBadRequest());
	}

	@Test
	void rewardCannotBeClaimedWhenHarvestMissionIsIncomplete() throws Exception {
		dailyService.completePlantMission(character.getId());

		performClaimReward().andExpect(status().isBadRequest());
	}

	@Test
	void rewardCannotBeClaimedTwice() throws Exception {
		completeBothMissions();
		performClaimReward().andExpect(status().isOk());

		performClaimReward()
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("이미 일일 미션 보상을 수령했습니다."));
	}

	@Test
	void rewardCannotBeClaimedWithoutAuthentication() throws Exception {
		mockMvc.perform(post("/api/daily/reward"))
				.andExpect(status().isUnauthorized());
	}

	private void completeBothMissions() {
		dailyService.completePlantMission(character.getId());
		dailyService.completeHarvestMission(character.getId());
	}

	private Seed findFlowerSeed() {
		return seedRepository.findByInventoryIdAndSeedType(inventory.getId(), SeedType.FLOWER).orElseThrow();
	}

	private org.springframework.test.web.servlet.ResultActions performClaimReward() throws Exception {
		return mockMvc.perform(post("/api/daily/reward")
				.header("Authorization", "Bearer " + accessToken));
	}

	private void deleteTestData() {
		dailyMissionRepository.deleteAll();
		seedRepository.deleteAll();
		inventoryRepository.deleteAll();
		houseRepository.deleteAll();
		regionRepository.deleteAll();
		worldRepository.deleteAll();
		characterRepository.deleteAll();
		userRepository.deleteAll();
	}
}
