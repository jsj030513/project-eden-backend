package com.projecteden.npc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.contains;
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
import com.projecteden.npc.repository.NpcRepository;
import com.projecteden.region.domain.RegionType;
import com.projecteden.region.repository.RegionRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NpcIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private NpcRepository npcRepository;

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
	private World world;

	@BeforeEach
	void setUp() throws Exception {
		deleteTestData();

		User user = userRepository.save(new User(
				"npc@example.com",
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
		accessToken = jwtTokenProvider.generateAccessToken(user);

		mockMvc.perform(post("/api/worlds")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isCreated());
		world = worldRepository.findByCharacterId(character.getId()).orElseThrow();
	}

	@AfterEach
	void cleanUp() {
		deleteTestData();
	}

	@Test
	void worldCreationCreatesDefaultNpcs() {
		assertEquals(5, npcRepository.findByRegionWorldId(world.getId()).size());
	}

	@Test
	void getMyNpcsReturnsFiveNpcs() throws Exception {
		performGetMyNpcs()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(5)));
	}

	@Test
	void npcsAreConnectedToRegions() throws Exception {
		performGetMyNpcs()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].regionType", containsInAnyOrder(
						"VILLAGE", "VILLAGE", "VILLAGE", "VILLAGE", "FLOWER_FIELD")));
	}

	@Test
	void villageContainsFourDefaultNpcs() throws Exception {
		Long villageId = regionRepository
				.findByWorldIdAndRegionType(world.getId(), RegionType.VILLAGE)
				.orElseThrow()
				.getId();
		assertEquals(4, npcRepository.findByRegionId(villageId).size());

		performGetMyNpcs()
				.andExpect(jsonPath("$[*].npcType", containsInAnyOrder(
						"VILLAGE_CHIEF", "GARDENER", "CARPENTER", "MERCHANT", "ARCHIVIST")));
	}

	@Test
	void flowerFieldContainsGardener() throws Exception {
		performGetMyNpcs()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.npcType == 'GARDENER')].regionType")
						.value(contains("FLOWER_FIELD")))
				.andExpect(jsonPath("$[?(@.npcType == 'GARDENER')].npcName")
						.value(contains("정원사 릴리")));
	}

	@Test
	void getMyNpcsFailsWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/npcs/me"))
				.andExpect(status().isUnauthorized());
	}

	private org.springframework.test.web.servlet.ResultActions performGetMyNpcs() throws Exception {
		return mockMvc.perform(get("/api/npcs/me")
				.header("Authorization", "Bearer " + accessToken));
	}

	private void deleteTestData() {
		npcRepository.deleteAll();
		regionRepository.deleteAll();
		worldRepository.deleteAll();
		characterRepository.deleteAll();
		userRepository.deleteAll();
	}
}
