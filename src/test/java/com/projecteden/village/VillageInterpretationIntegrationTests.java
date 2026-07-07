package com.projecteden.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.auth.jwt.JwtTokenProvider;
import com.projecteden.character.domain.Character;
import com.projecteden.character.domain.CharacterGender;
import com.projecteden.character.domain.CharacterJob;
import com.projecteden.character.domain.HairStyle;
import com.projecteden.character.domain.Outfit;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.village.domain.VillageCategory;
import com.projecteden.village.domain.VillageHistoryType;
import com.projecteden.village.domain.VillageTheme;
import com.projecteden.village.repository.VillageChangeRepository;
import com.projecteden.village.repository.VillageHistoryRepository;
import com.projecteden.village.repository.VillageMemoryRepository;
import com.projecteden.village.repository.VillageThemeSnapshotRepository;
import com.projecteden.village.service.VillageInterpretationService;
import com.projecteden.village.service.VillageService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VillageInterpretationIntegrationTests {

	@Autowired MockMvc mockMvc;
	@Autowired VillageInterpretationService interpretationService;
	@Autowired VillageService villageService;
	@Autowired VillageThemeSnapshotRepository snapshots;
	@Autowired VillageHistoryRepository histories;
	@Autowired VillageChangeRepository changes;
	@Autowired VillageMemoryRepository memories;
	@Autowired CharacterRepository characters;
	@Autowired UserRepository users;
	@Autowired PasswordEncoder encoder;
	@Autowired JwtTokenProvider tokens;

	private User user;
	private Character character;
	private String token;

	@BeforeEach
	void setUp() {
		clean();
		user = users.save(new User("interpretation@example.com", encoder.encode("password123"), "기억이"));
		character = characters.save(Character.create(user, "에덴", CharacterGender.NONE,
				HairStyle.PIXEL_CUT, "brown", Outfit.ROBE, CharacterJob.WIZARD));
		token = tokens.generateAccessToken(user);
	}

	@AfterEach
	void tearDown() {
		clean();
	}

	@Test
	void noMemoryUsesUndefinedTheme() {
		var response = interpretationService.getInterpretation(character.getId());
		assertEquals(VillageTheme.UNDEFINED, response.theme());
		assertNull(response.primaryCategory());
		assertTrue(response.expressions().isEmpty());
	}

	@Test
	void natureMemoryUsesBloomingTheme() {
		record(RecognizedObject.FLOWER, 1);
		assertEquals(VillageTheme.BLOOMING_VILLAGE, interpretation().theme());
	}

	@Test
	void foodMemoryUsesWarmTheme() {
		record(RecognizedObject.TOMATO, 1);
		assertEquals(VillageTheme.WARM_VILLAGE, interpretation().theme());
	}

	@Test
	void unknownMemoryUsesQuietTheme() {
		record(RecognizedObject.UNKNOWN, 1);
		assertEquals(VillageTheme.QUIET_VILLAGE, interpretation().theme());
	}

	@Test
	void primaryAndSecondaryCategoriesAreReturned() {
		saveMemory(VillageCategory.FOOD, 1);
		saveMemory(VillageCategory.NATURE, 2);
		var response = interpretation();
		assertEquals(VillageCategory.NATURE, response.primaryCategory());
		assertEquals(VillageCategory.FOOD, response.secondaryCategory());
	}

	@Test
	void tieUsesNatureBeforeFood() {
		saveMemory(VillageCategory.FOOD, 1);
		saveMemory(VillageCategory.NATURE, 1);
		var response = interpretation();
		assertEquals(VillageCategory.NATURE, response.primaryCategory());
		assertEquals(VillageCategory.FOOD, response.secondaryCategory());
	}

	@Test
	void interpretationCreatesOneSnapshot() {
		interpretationService.getInterpretation(character.getId());
		interpretationService.getInterpretation(character.getId());
		assertEquals(1, snapshots.count());
		assertEquals("v1", snapshots.findByCharacterId(character.getId()).orElseThrow().getRuleVersion());
	}

	@Test
	void sameThemeRetainsAppliedAt() {
		record(RecognizedObject.FLOWER, 1);
		LocalDateTime appliedAt = snapshot().getAppliedAt();
		record(RecognizedObject.FLOWER, 1);
		assertEquals(appliedAt, snapshot().getAppliedAt());
	}

	@Test
	void insufficientDifferenceRetainsCurrentTheme() {
		record(RecognizedObject.FLOWER, 1);
		record(RecognizedObject.TOMATO, 3);
		assertEquals(VillageTheme.BLOOMING_VILLAGE, interpretation().theme());
	}

	@Test
	void differenceOfThreeChangesTheme() {
		record(RecognizedObject.FLOWER, 1);
		record(RecognizedObject.TOMATO, 4);
		assertEquals(VillageTheme.WARM_VILLAGE, interpretation().theme());
	}

	@Test
	void actualThemeChangeCreatesHistory() {
		interpretationService.getInterpretation(character.getId());
		record(RecognizedObject.FLOWER, 1);
		assertEquals(1, themeChangeHistoryCount());
	}

	@Test
	void unchangedThemeDoesNotCreateThemeHistory() {
		record(RecognizedObject.FLOWER, 2);
		assertEquals(0, themeChangeHistoryCount());
	}

	@Test
	void expressionIsDeterministicForTheme() {
		record(RecognizedObject.FLOWER, 1);
		var response = interpretation();
		assertEquals("이 마을은 꽃과 바람이 오래 머무는 곳이 되어가고 있습니다.", response.message());
		assertEquals("NPC_DIALOGUE", response.expressions().getFirst().type());
	}

	@Test
	void interpretationApiReturnsCurrentTheme() throws Exception {
		record(RecognizedObject.FLOWER, 1);
		mockMvc.perform(get("/api/village/interpretation")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.theme").value("BLOOMING_VILLAGE"))
				.andExpect(jsonPath("$.primaryCategory").value("NATURE"))
				.andExpect(jsonPath("$.ruleVersion").value("v1"));
	}

	@Test
	void interpretationApiRequiresJwt() throws Exception {
		mockMvc.perform(get("/api/village/interpretation"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void memoryRecordingAutomaticallyUpdatesSnapshot() {
		record(RecognizedObject.FLOWER, 1);
		assertNotNull(snapshot().getAppliedAt());
		assertEquals(VillageTheme.BLOOMING_VILLAGE, snapshot().getTheme());
	}

	private com.projecteden.village.dto.VillageInterpretationResponse interpretation() {
		return interpretationService.getInterpretation(character.getId());
	}

	private com.projecteden.village.domain.VillageThemeSnapshot snapshot() {
		return snapshots.findByCharacterId(character.getId()).orElseThrow();
	}

	private void record(RecognizedObject object, int count) {
		for (int i = 0; i < count; i++) {
			villageService.recordVillageMemory(character.getId(), object);
		}
	}

	private void saveMemory(VillageCategory category, int count) {
		var memory = com.projecteden.village.domain.VillageMemory.create(
				character, category, LocalDateTime.now());
		for (int i = 1; i < count; i++) {
			memory.record(LocalDateTime.now());
		}
		memories.save(memory);
	}

	private long themeChangeHistoryCount() {
		return histories.findByCharacterIdOrderByCreatedAtDesc(character.getId()).stream()
				.filter(history -> history.getHistoryType() == VillageHistoryType.THEME_CHANGED)
				.count();
	}

	private void clean() {
		histories.deleteAll();
		changes.deleteAll();
		snapshots.deleteAll();
		memories.deleteAll();
		characters.deleteAll();
		users.deleteAll();
	}
}
