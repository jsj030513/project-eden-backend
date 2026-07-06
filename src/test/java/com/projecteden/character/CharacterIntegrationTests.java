package com.projecteden.character;

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
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.tutorial.repository.TutorialProgressRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CharacterIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CharacterRepository characterRepository;

	@Autowired
	private TutorialProgressRepository tutorialProgressRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	private User user;
	private String accessToken;

	@BeforeEach
	void setUp() {
		tutorialProgressRepository.deleteAll();
		characterRepository.deleteAll();
		userRepository.deleteAll();
		user = userRepository.save(new User(
				"character@example.com",
				passwordEncoder.encode("password123"),
				"eden"));
		accessToken = jwtTokenProvider.generateAccessToken(user);
	}

	@AfterEach
	void cleanUp() {
		tutorialProgressRepository.deleteAll();
		characterRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void createCharacterSucceeds() throws Exception {
		performCreate("BEGINNER")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.userId").value(user.getId()))
				.andExpect(jsonPath("$.name").value("에덴"))
				.andExpect(jsonPath("$.gender").value("NONE"))
				.andExpect(jsonPath("$.hairStyle").value("PIXEL_CUT"))
				.andExpect(jsonPath("$.hairColor").value("brown"))
				.andExpect(jsonPath("$.outfit").value("ROBE"))
				.andExpect(jsonPath("$.job").value("BEGINNER"))
				.andExpect(jsonPath("$.weaponType").value("NONE"))
				.andExpect(jsonPath("$.level").value(1))
				.andExpect(jsonPath("$.exp").value(0))
				.andExpect(jsonPath("$.energy").value(100));
	}

	@Test
	void wizardUsesStaff() throws Exception {
		performCreate("WIZARD")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.weaponType").value("STAFF"));
	}

	@Test
	void warriorUsesSword() throws Exception {
		performCreate("WARRIOR")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.weaponType").value("SWORD"));
	}

	@Test
	void breederUsesFeedBasket() throws Exception {
		performCreate("BREEDER")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.weaponType").value("FEED_BASKET"));
	}

	@Test
	void duplicateCharacterFails() throws Exception {
		performCreate("WIZARD").andExpect(status().isCreated());

		performCreate("WARRIOR")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("이미 캐릭터가 존재합니다."));
	}

	@Test
	void getMyCharacterSucceeds() throws Exception {
		performCreate("WIZARD").andExpect(status().isCreated());

		mockMvc.perform(get("/api/characters/me")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(user.getId()))
				.andExpect(jsonPath("$.name").value("에덴"))
				.andExpect(jsonPath("$.job").value("WIZARD"))
				.andExpect(jsonPath("$.weaponType").value("STAFF"));
	}

	@Test
	void getMyCharacterFailsWhenCharacterDoesNotExist() throws Exception {
		mockMvc.perform(get("/api/characters/me")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("캐릭터를 찾을 수 없습니다."));
	}

	@Test
	void createCharacterFailsWithoutAuthentication() throws Exception {
		mockMvc.perform(post("/api/characters")
				.contentType(MediaType.APPLICATION_JSON)
				.content(characterRequest("WIZARD")))
				.andExpect(status().isUnauthorized());
	}

	private org.springframework.test.web.servlet.ResultActions performCreate(String job) throws Exception {
		return mockMvc.perform(post("/api/characters")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(characterRequest(job)));
	}

	private String characterRequest(String job) {
		return """
				{
				  "name": "에덴",
				  "gender": "NONE",
				  "hairStyle": "PIXEL_CUT",
				  "hairColor": "brown",
				  "outfit": "ROBE",
				  "job": "%s"
				}
				""".formatted(job);
	}
}
