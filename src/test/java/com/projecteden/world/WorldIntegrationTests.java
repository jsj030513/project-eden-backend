package com.projecteden.world;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import com.projecteden.region.repository.RegionRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorldIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private WorldRepository worldRepository;

	@Autowired
	private RegionRepository regionRepository;

	@Autowired
	private NpcRepository npcRepository;

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
	void setUp() {
		npcRepository.deleteAll();
		regionRepository.deleteAll();
		worldRepository.deleteAll();
		characterRepository.deleteAll();
		userRepository.deleteAll();

		User user = userRepository.save(new User(
				"world@example.com",
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
		accessToken = jwtTokenProvider.generateAccessToken(user);
	}

	@AfterEach
	void cleanUp() {
		npcRepository.deleteAll();
		regionRepository.deleteAll();
		worldRepository.deleteAll();
		characterRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void createWorldSucceeds() throws Exception {
		performCreate()
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.worldName").value("에덴의 세계"))
				.andExpect(jsonPath("$.gold").value(100))
				.andExpect(jsonPath("$.wood").value(20))
				.andExpect(jsonPath("$.stone").value(10))
				.andExpect(jsonPath("$.food").value(20));

		World world = worldRepository.findByCharacterId(character.getId()).orElseThrow();
		assertNotEquals(0L, world.getSeed());
	}

	@Test
	void duplicateWorldFails() throws Exception {
		performCreate().andExpect(status().isCreated());

		performCreate()
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("이미 월드가 존재합니다."));
	}

	@Test
	void getMyWorldSucceeds() throws Exception {
		performCreate().andExpect(status().isCreated());

		mockMvc.perform(get("/api/worlds/me")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.worldName").value("에덴의 세계"))
				.andExpect(jsonPath("$.gold").value(100));
	}

	@Test
	void createWorldFailsWithoutAuthentication() throws Exception {
		mockMvc.perform(post("/api/worlds"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void seasonDefaultsToSpring() throws Exception {
		performCreate()
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.season").value("SPRING"));
	}

	@Test
	void weatherDefaultsToSunny() throws Exception {
		performCreate()
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.weather").value("SUNNY"));
	}

	@Test
	void dayDefaultsToOne() throws Exception {
		performCreate()
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.day").value(1));
	}

	private org.springframework.test.web.servlet.ResultActions performCreate() throws Exception {
		return mockMvc.perform(post("/api/worlds")
				.header("Authorization", "Bearer " + accessToken));
	}
}
