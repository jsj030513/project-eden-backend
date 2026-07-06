package com.projecteden.seed;

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
import com.projecteden.seed.domain.SeedType;
import com.projecteden.seed.repository.SeedRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeedIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SeedRepository seedRepository;

	@Autowired
	private InventoryRepository inventoryRepository;

	@Autowired
	private HouseRepository houseRepository;

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
				"seed@example.com",
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
	void inventoryCreationGrantsStarterSeeds() throws Exception {
		mockMvc.perform(get("/api/seeds/me")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void starterSeedsContainFiveFlowers() throws Exception {
		mockMvc.perform(get("/api/seeds/me")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].seedType").value("FLOWER"))
				.andExpect(jsonPath("$[0].quantity").value(5));
	}

	@Test
	void plantSeedSucceeds() throws Exception {
		performPlant()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.seedType").value("FLOWER"));
	}

	@Test
	void plantingDecreasesQuantity() throws Exception {
		performPlant()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.remaining").value(4));

		assertEquals(4, seedRepository.findAll().getFirst().getQuantity());
	}

	@Test
	void plantingFailsWhenSeedsAreEmpty() throws Exception {
		for (int i = 0; i < 5; i++) {
			performPlant().andExpect(status().isOk());
		}

		performPlant()
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("씨앗이 부족합니다."));
	}

	@Test
	void getSeedsFailsWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/seeds/me"))
				.andExpect(status().isUnauthorized());
	}

	private org.springframework.test.web.servlet.ResultActions performPlant() throws Exception {
		return mockMvc.perform(post("/api/seeds/plant")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "seedType": "%s"
						}
						""".formatted(SeedType.FLOWER.name())));
	}

	private void deleteTestData() {
		seedRepository.deleteAll();
		inventoryRepository.deleteAll();
		houseRepository.deleteAll();
		worldRepository.deleteAll();
		characterRepository.deleteAll();
		userRepository.deleteAll();
	}
}
