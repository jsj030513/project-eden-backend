package com.projecteden.region;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
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
import com.projecteden.region.repository.RegionRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.world.repository.WorldRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegionIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

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

	private String accessToken;

	@BeforeEach
	void setUp() throws Exception {
		deleteTestData();

		User user = userRepository.save(new User(
				"region@example.com",
				passwordEncoder.encode("password123"),
				"eden"));
		characterRepository.save(Character.create(
				user,
				"에덴",
				CharacterGender.NONE,
				HairStyle.PIXEL_CUT,
				"brown",
				Outfit.ROBE,
				CharacterJob.WIZARD));
		accessToken = jwtTokenProvider.generateAccessToken(user);

		mockMvc.perform(post("/api/worlds")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isCreated());
	}

	@AfterEach
	void cleanUp() {
		deleteTestData();
	}

	@Test
	void worldCreationCreatesFiveRegions() {
		org.junit.jupiter.api.Assertions.assertEquals(5, regionRepository.count());
	}

	@Test
	void getMyRegionsReturnsFiveRegions() throws Exception {
		performGetMyRegions()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(5)));
	}

	@Test
	void regionTypesAreCreated() throws Exception {
		performGetMyRegions()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].regionType", containsInAnyOrder(
						"VILLAGE", "FOREST", "RIVER", "HILL", "FLOWER_FIELD")));
	}

	@Test
	void displayNamesAreCreated() throws Exception {
		performGetMyRegions()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].displayName", containsInAnyOrder(
						"마을", "숲", "강", "언덕", "꽃밭")))
				.andExpect(jsonPath("$[*].unlocked", containsInAnyOrder(
						true, true, true, true, true)));
	}

	@Test
	void getMyRegionsFailsWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/regions/me"))
				.andExpect(status().isUnauthorized());
	}

	private org.springframework.test.web.servlet.ResultActions performGetMyRegions() throws Exception {
		return mockMvc.perform(get("/api/regions/me")
				.header("Authorization", "Bearer " + accessToken));
	}

	private void deleteTestData() {
		regionRepository.deleteAll();
		worldRepository.deleteAll();
		characterRepository.deleteAll();
		userRepository.deleteAll();
	}
}
