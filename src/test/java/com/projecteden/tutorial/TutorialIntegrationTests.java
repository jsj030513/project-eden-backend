package com.projecteden.tutorial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.tutorial.domain.TutorialProgress;
import com.projecteden.tutorial.repository.TutorialProgressRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TutorialIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TutorialProgressRepository tutorialProgressRepository;

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
	void setUp() throws Exception {
		deleteTestData();

		User user = userRepository.save(new User(
				"tutorial@example.com",
				passwordEncoder.encode("password123"),
				"eden"));
		accessToken = jwtTokenProvider.generateAccessToken(user);

		mockMvc.perform(post("/api/characters")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name": "에덴",
						  "gender": "NONE",
						  "hairStyle": "PIXEL_CUT",
						  "hairColor": "brown",
						  "outfit": "ROBE",
						  "job": "WIZARD"
						}
						"""))
				.andExpect(status().isCreated());
		character = characterRepository.findByUserId(user.getId()).orElseThrow();
	}

	@AfterEach
	void cleanUp() {
		deleteTestData();
	}

	@Test
	void characterCreationCreatesTutorial() {
		org.junit.jupiter.api.Assertions.assertTrue(
				tutorialProgressRepository.existsByCharacterId(character.getId()));
	}

	@Test
	void tutorialStartsAtWelcome() throws Exception {
		mockMvc.perform(get("/api/tutorial/me")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentStep").value("WELCOME"))
				.andExpect(jsonPath("$.completed").value(false));
	}

	@Test
	void tutorialAdvancesToNextStep() throws Exception {
		performAdvance("MEET_CHIEF")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentStep").value("MEET_CHIEF"))
				.andExpect(jsonPath("$.completed").value(false));
	}

	@Test
	void tutorialCompletesAtFinished() throws Exception {
		performAdvance("MEET_CHIEF").andExpect(status().isOk());
		performAdvance("CHECK_HOME").andExpect(status().isOk());
		performAdvance("CHECK_INVENTORY").andExpect(status().isOk());
		performAdvance("VISIT_FLOWER_FIELD").andExpect(status().isOk());
		performAdvance("FINISHED")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentStep").value("FINISHED"))
				.andExpect(jsonPath("$.completed").value(true));

		TutorialProgress progress = tutorialProgressRepository
				.findByCharacterId(character.getId())
				.orElseThrow();
		assertEquals("FINISHED", progress.getCurrentStep().name());
		assertNotNull(progress.getCompletedAt());
	}

	@Test
	void tutorialCannotMoveBackward() throws Exception {
		performAdvance("MEET_CHIEF").andExpect(status().isOk());

		performAdvance("WELCOME")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message")
						.value("튜토리얼 단계를 순서대로 진행해야 합니다."));
	}

	@Test
	void tutorialCannotSkipStep() throws Exception {
		performAdvance("CHECK_HOME")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message")
						.value("튜토리얼 단계를 순서대로 진행해야 합니다."));
	}

	@Test
	void tutorialFailsWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/tutorial/me"))
				.andExpect(status().isUnauthorized());
	}

	private org.springframework.test.web.servlet.ResultActions performAdvance(String nextStep) throws Exception {
		return mockMvc.perform(patch("/api/tutorial/advance")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "nextStep": "%s"
						}
						""".formatted(nextStep)));
	}

	private void deleteTestData() {
		tutorialProgressRepository.deleteAll();
		characterRepository.deleteAll();
		userRepository.deleteAll();
	}
}
