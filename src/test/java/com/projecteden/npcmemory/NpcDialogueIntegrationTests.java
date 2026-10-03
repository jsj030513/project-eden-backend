package com.projecteden.npcmemory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.projecteden.auth.jwt.JwtTokenProvider;
import com.projecteden.character.domain.Character;
import com.projecteden.character.domain.CharacterGender;
import com.projecteden.character.domain.CharacterJob;
import com.projecteden.character.domain.HairStyle;
import com.projecteden.character.domain.Outfit;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.npc.domain.Npc;
import com.projecteden.npc.repository.NpcRepository;
import com.projecteden.npcmemory.dialogue.NpcDialogueKey;
import com.projecteden.npcmemory.repository.NpcMemoryRepository;
import com.projecteden.region.repository.RegionRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.village.domain.VillageCategory;
import com.projecteden.village.domain.VillageHistory;
import com.projecteden.village.domain.VillageHistoryType;
import com.projecteden.village.domain.VillageTheme;
import com.projecteden.village.domain.VillageThemeSnapshot;
import com.projecteden.village.repository.VillageHistoryRepository;
import com.projecteden.village.repository.VillageThemeSnapshotRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NpcDialogueIntegrationTests {

	@Autowired MockMvc mockMvc;
	@Autowired JwtTokenProvider tokens;
	@Autowired PasswordEncoder encoder;
	@Autowired UserRepository users;
	@Autowired CharacterRepository characters;
	@Autowired WorldRepository worlds;
	@Autowired RegionRepository regions;
	@Autowired NpcRepository npcs;
	@Autowired NpcMemoryRepository npcMemories;
	@Autowired VillageThemeSnapshotRepository snapshots;
	@Autowired VillageHistoryRepository histories;

	private User user;
	private Character character;
	private String token;
	private Npc npc;

	@BeforeEach
	void setUp() throws Exception {
		clean();

		user = users.save(new User("npc-dialogue-api@example.com",
				encoder.encode("password123"), "대화주민"));
		character = characters.save(Character.create(user, "에덴", CharacterGender.NONE,
				HairStyle.PIXEL_CUT, "brown", Outfit.ROBE, CharacterJob.WIZARD));
		token = tokens.generateAccessToken(user);

		mockMvc.perform(post("/api/worlds")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isCreated());
		World world = worlds.findByCharacterId(character.getId()).orElseThrow();
		npc = npcs.findByRegionWorldId(world.getId()).getFirst();
		snapshots.save(VillageThemeSnapshot.create(character, VillageTheme.BLOOMING_VILLAGE,
				VillageCategory.NATURE, null, LocalDateTime.now()));
	}

	@AfterEach
	void tearDown() {
		clean();
	}

	@Test
	void dialogueRequiresJwt() throws Exception {
		mockMvc.perform(get("/api/npcs/{npcId}/dialogue", npc.getId()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void unknownNpcReturnsNotFound() throws Exception {
		dialogue(999999L, token)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("NPC를 찾을 수 없습니다."));
	}

	@Test
	void firstDialogueReturnsFirstMeetingAndCreatesMemory() throws Exception {
		dialogue(npc.getId(), token)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.npcId").value(npc.getId()))
				.andExpect(jsonPath("$.dialogueKey").value("FIRST_MEETING"))
				.andExpect(jsonPath("$.message").value("처음 보는 풍경인데도 이상하게 따뜻하네요."))
				.andExpect(jsonPath("$.currentTheme").value("BLOOMING_VILLAGE"))
				.andExpect(jsonPath("$.rememberedCategory").value("NATURE"))
				.andExpect(jsonPath("$.memoryChanged").value(true))
				.andExpect(jsonPath("$.spokenAt").exists())
				.andExpect(jsonPath("$.interactionCount").doesNotExist())
				.andExpect(jsonPath("$.lastDialogueKey").doesNotExist())
				.andExpect(jsonPath("$.percentage").doesNotExist())
				.andExpect(jsonPath("$.memoryCount").doesNotExist());

		var memory = npcMemories.findByCharacterIdAndNpcId(character.getId(), npc.getId())
				.orElseThrow();
		assertEquals(1, memory.getInteractionCount());
		assertEquals(NpcDialogueKey.FIRST_MEETING.name(), memory.getLastDialogueKey());
		assertEquals(VillageTheme.BLOOMING_VILLAGE, memory.getRememberedTheme());
		assertEquals(VillageCategory.NATURE, memory.getRememberedCategory());
	}

	@Test
	void secondDialogueDoesNotReturnFirstMeetingAndUsesThemeDialogue() throws Exception {
		dialogue(npc.getId(), token).andExpect(status().isOk());

		dialogue(npc.getId(), token)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dialogueKey").value("BLOOMING_FIRST"))
				.andExpect(jsonPath("$.message").value("꽃이 이 길을 오래 바라보고 있나 봐요."));
	}

	@Test
	void repeatedThemeDialogueAlternatesReturningAndRepeatAlt() throws Exception {
		dialogue(npc.getId(), token).andExpect(status().isOk());
		dialogue(npc.getId(), token).andExpect(status().isOk());

		dialogue(npc.getId(), token)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dialogueKey").value("BLOOMING_RETURNING"));
		dialogue(npc.getId(), token)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dialogueKey").value("BLOOMING_REPEAT_ALT"));
		dialogue(npc.getId(), token)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dialogueKey").value("BLOOMING_RETURNING"));
	}

	@Test
	void differentNpcCreatesSeparateMemory() throws Exception {
		World world = worlds.findByCharacterId(character.getId()).orElseThrow();
		Npc otherNpc = npcs.findByRegionWorldId(world.getId()).stream()
				.filter(candidate -> !candidate.getId().equals(npc.getId()))
				.findFirst()
				.orElseThrow();

		dialogue(npc.getId(), token).andExpect(status().isOk());
		dialogue(otherNpc.getId(), token).andExpect(status().isOk());

		assertEquals(1, npcMemories.findByCharacterIdAndNpcId(character.getId(), npc.getId())
				.orElseThrow().getInteractionCount());
		assertEquals(1, npcMemories.findByCharacterIdAndNpcId(character.getId(), otherNpc.getId())
				.orElseThrow().getInteractionCount());
		assertEquals(2, npcMemories.count());
	}

	@Test
	void differentUserCreatesSeparateMemoryForSameNpc() throws Exception {
		User otherUser = users.save(new User("npc-dialogue-other@example.com",
				encoder.encode("password123"), "다른대화"));
		Character otherCharacter = characters.save(Character.create(otherUser, "다른에덴",
				CharacterGender.NONE, HairStyle.PIXEL_CUT, "black", Outfit.ROBE,
				CharacterJob.WIZARD));
		String otherToken = tokens.generateAccessToken(otherUser);

		dialogue(npc.getId(), token).andExpect(status().isOk());
		dialogue(npc.getId(), otherToken).andExpect(status().isOk());

		assertEquals(1, npcMemories.findByCharacterIdAndNpcId(character.getId(), npc.getId())
				.orElseThrow().getInteractionCount());
		assertEquals(1, npcMemories.findByCharacterIdAndNpcId(otherCharacter.getId(), npc.getId())
				.orElseThrow().getInteractionCount());
		assertEquals(2, npcMemories.count());
	}

	@Test
	void recentThemeChangedHasPriority() throws Exception {
		dialogue(npc.getId(), token).andExpect(status().isOk());
		histories.save(VillageHistory.create(character, VillageHistoryType.THEME_CHANGED,
				VillageCategory.NATURE, null, "원문은 응답에 쓰지 않음", LocalDateTime.now()));

		dialogue(npc.getId(), token)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dialogueKey").value("RECENT_THEME_CHANGE"))
				.andExpect(jsonPath("$.message").value("마을의 공기가 조금 달라진 것 같아요."));
	}

	@Test
	void recentChangeAppearedHasPriority() throws Exception {
		dialogue(npc.getId(), token).andExpect(status().isOk());
		histories.save(VillageHistory.create(character, VillageHistoryType.CHANGE_APPEARED,
				VillageCategory.WALK, null, "원문은 응답에 쓰지 않음", LocalDateTime.now()));

		dialogue(npc.getId(), token)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dialogueKey").value("RECENT_CHANGE_APPEARED"))
				.andExpect(jsonPath("$.rememberedCategory").value("WALK"));
	}

	@Test
	void recentMemoryRecordedHasPriority() throws Exception {
		dialogue(npc.getId(), token).andExpect(status().isOk());
		histories.save(VillageHistory.create(character, VillageHistoryType.MEMORY_RECORDED,
				VillageCategory.ANIMAL, null, "원문은 응답에 쓰지 않음", LocalDateTime.now()));

		dialogue(npc.getId(), token)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dialogueKey").value("RECENT_MEMORY_RECORDED"))
				.andExpect(jsonPath("$.rememberedCategory").value("ANIMAL"));
	}

	@Test
	void oldHistoryIsNotUsedAsRecent() throws Exception {
		dialogue(npc.getId(), token).andExpect(status().isOk());
		histories.save(VillageHistory.create(character, VillageHistoryType.MEMORY_RECORDED,
				VillageCategory.ANIMAL, null, "오래된 기록", LocalDateTime.now().minusHours(25)));

		dialogue(npc.getId(), token)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dialogueKey").value("BLOOMING_FIRST"));
	}

	private org.springframework.test.web.servlet.ResultActions dialogue(
			Long npcId, String accessToken) throws Exception {
		return mockMvc.perform(get("/api/npcs/{npcId}/dialogue", npcId)
				.header("Authorization", "Bearer " + accessToken));
	}

	private void clean() {
		npcMemories.deleteAll();
		histories.deleteAll();
		snapshots.deleteAll();
		npcs.deleteAll();
		regions.deleteAll();
		worlds.deleteAll();
		characters.deleteAll();
		users.deleteAll();
	}
}
