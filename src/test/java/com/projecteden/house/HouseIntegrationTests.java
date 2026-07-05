package com.projecteden.house;

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
import com.projecteden.house.repository.HouseRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HouseIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

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
				"house@example.com",
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
		worldRepository.save(World.create(character, 12345L));
		accessToken = jwtTokenProvider.generateAccessToken(user);
	}

	@AfterEach
	void cleanUp() {
		deleteTestData();
	}

	@Test
	void createHouseSucceeds() throws Exception {
		performCreate()
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.houseName").value("에덴의 집"))
				.andExpect(jsonPath("$.maxDecoration").value(10));
	}

	@Test
	void duplicateHouseFails() throws Exception {
		performCreate().andExpect(status().isCreated());

		performCreate()
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("이미 집이 존재합니다."));
	}

	@Test
	void getMyHouseSucceeds() throws Exception {
		performCreate().andExpect(status().isCreated());

		mockMvc.perform(get("/api/houses/me")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.houseName").value("에덴의 집"))
				.andExpect(jsonPath("$.level").value(1))
				.andExpect(jsonPath("$.houseType").value("CABIN"))
				.andExpect(jsonPath("$.maxDecoration").value(10));
	}

	@Test
	void createHouseFailsWithoutAuthentication() throws Exception {
		mockMvc.perform(post("/api/houses"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void levelDefaultsToOne() throws Exception {
		performCreate()
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.level").value(1));
	}

	@Test
	void houseTypeDefaultsToCabin() throws Exception {
		performCreate()
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.houseType").value("CABIN"));
	}

	private org.springframework.test.web.servlet.ResultActions performCreate() throws Exception {
		return mockMvc.perform(post("/api/houses")
				.header("Authorization", "Bearer " + accessToken));
	}

	private void deleteTestData() {
		houseRepository.deleteAll();
		worldRepository.deleteAll();
		characterRepository.deleteAll();
		userRepository.deleteAll();
	}
}
