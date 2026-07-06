package com.projecteden.inventory;

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
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

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
	void setUp() {
		deleteTestData();

		User user = userRepository.save(new User(
				"inventory@example.com",
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
	}

	@AfterEach
	void cleanUp() {
		deleteTestData();
	}

	@Test
	void createInventorySucceeds() throws Exception {
		performCreate()
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.capacity").value(30))
				.andExpect(jsonPath("$.usedSlot").value(0));
	}

	@Test
	void duplicateInventoryFails() throws Exception {
		performCreate().andExpect(status().isCreated());

		performCreate()
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("이미 인벤토리가 존재합니다."));
	}

	@Test
	void getMyInventorySucceeds() throws Exception {
		performCreate().andExpect(status().isCreated());

		mockMvc.perform(get("/api/inventories/me")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.capacity").value(30))
				.andExpect(jsonPath("$.usedSlot").value(0));
	}

	@Test
	void createInventoryFailsWithoutAuthentication() throws Exception {
		mockMvc.perform(post("/api/inventories"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void capacityDefaultsToThirty() throws Exception {
		performCreate()
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.capacity").value(30));
	}

	@Test
	void usedSlotDefaultsToZero() throws Exception {
		performCreate()
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.usedSlot").value(0));
	}

	private org.springframework.test.web.servlet.ResultActions performCreate() throws Exception {
		return mockMvc.perform(post("/api/inventories")
				.header("Authorization", "Bearer " + accessToken));
	}

	private void deleteTestData() {
		inventoryRepository.deleteAll();
		houseRepository.deleteAll();
		worldRepository.deleteAll();
		characterRepository.deleteAll();
		userRepository.deleteAll();
	}
}
