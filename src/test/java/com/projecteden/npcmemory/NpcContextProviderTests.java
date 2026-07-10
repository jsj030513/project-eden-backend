package com.projecteden.npcmemory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.projecteden.character.domain.Character;
import com.projecteden.character.domain.CharacterGender;
import com.projecteden.character.domain.CharacterJob;
import com.projecteden.character.domain.HairStyle;
import com.projecteden.character.domain.Outfit;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.npcmemory.context.NpcContextProvider;
import com.projecteden.npcmemory.repository.NpcMemoryRepository;
import com.projecteden.npcmemory.service.NpcMemoryService;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.village.domain.VillageCategory;
import com.projecteden.village.domain.VillageHistory;
import com.projecteden.village.domain.VillageHistoryType;
import com.projecteden.village.domain.VillageTheme;
import com.projecteden.village.domain.VillageThemeSnapshot;
import com.projecteden.village.repository.VillageHistoryRepository;
import com.projecteden.village.repository.VillageThemeSnapshotRepository;

@SpringBootTest
@ActiveProfiles("test")
class NpcContextProviderTests {

	@Autowired NpcContextProvider contextProvider;
	@Autowired NpcMemoryService npcMemoryService;
	@Autowired NpcMemoryRepository npcMemories;
	@Autowired VillageThemeSnapshotRepository snapshots;
	@Autowired VillageHistoryRepository histories;
	@Autowired CharacterRepository characters;
	@Autowired UserRepository users;

	private Character character;

	@BeforeEach
	void setUp() {
		clean();
		User user = users.save(new User("npc-context@example.com", "password", "컨텍스트"));
		character = characters.save(Character.create(user, "에덴", CharacterGender.NONE,
				HairStyle.PIXEL_CUT, "brown", Outfit.ROBE, CharacterJob.WIZARD));
	}

	@AfterEach
	void tearDown() {
		clean();
	}

	@Test
	void buildContextHandlesMissingSnapshotAndMemory() {
		var context = contextProvider.buildContext(character.getId(), 10L);

		assertEquals(VillageTheme.UNDEFINED, context.currentTheme());
		assertNull(context.primaryCategory());
		assertNull(context.recentHistoryType());
		assertTrue(context.firstMeeting());
		assertEquals(0, context.interactionCount());
	}

	@Test
	void buildContextUsesSnapshotMemoryAndRecentHistory() {
		snapshots.save(VillageThemeSnapshot.create(character, VillageTheme.BLOOMING_VILLAGE,
				VillageCategory.NATURE, VillageCategory.WALK, LocalDateTime.now()));
		histories.save(VillageHistory.create(character, VillageHistoryType.THEME_CHANGED,
				VillageCategory.NATURE, null, "저장된 원문은 노출하지 않음", LocalDateTime.now()));
		npcMemoryService.recordInteraction(character.getId(), 10L, VillageTheme.WARM_VILLAGE,
				VillageCategory.FOOD, "WARM_FIRST");

		var context = contextProvider.buildContext(character.getId(), 10L);

		assertEquals(VillageTheme.BLOOMING_VILLAGE, context.currentTheme());
		assertEquals(VillageCategory.NATURE, context.primaryCategory());
		assertEquals(VillageCategory.WALK, context.secondaryCategory());
		assertEquals(VillageHistoryType.THEME_CHANGED, context.recentHistoryType());
		assertEquals(VillageCategory.NATURE, context.recentHistoryCategory());
		assertEquals(VillageCategory.FOOD, context.rememberedCategory());
		assertEquals(1, context.interactionCount());
		assertEquals("WARM_FIRST", context.lastDialogueKey());
	}

	@Test
	void historyOlderThanTwentyFourHoursIsNotRecent() {
		histories.save(VillageHistory.create(character, VillageHistoryType.MEMORY_RECORDED,
				VillageCategory.NATURE, null, "오래된 기록", LocalDateTime.now().minusHours(25)));

		var context = contextProvider.buildContext(character.getId(), 10L);

		assertNull(context.recentHistoryType());
		assertNull(context.recentHistoryCategory());
	}

	private void clean() {
		npcMemories.deleteAll();
		histories.deleteAll();
		snapshots.deleteAll();
		characters.deleteAll();
		users.deleteAll();
	}
}
