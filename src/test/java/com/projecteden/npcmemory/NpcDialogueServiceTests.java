package com.projecteden.npcmemory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.projecteden.npcmemory.dialogue.NpcDialogueKey;
import com.projecteden.npcmemory.repository.NpcMemoryRepository;
import com.projecteden.npcmemory.service.NpcDialogueService;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.village.domain.VillageCategory;
import com.projecteden.village.domain.VillageTheme;
import com.projecteden.village.domain.VillageThemeSnapshot;
import com.projecteden.village.repository.VillageHistoryRepository;
import com.projecteden.village.repository.VillageThemeSnapshotRepository;

@SpringBootTest
@ActiveProfiles("test")
class NpcDialogueServiceTests {

	@Autowired NpcDialogueService dialogueService;
	@Autowired NpcMemoryRepository npcMemories;
	@Autowired VillageThemeSnapshotRepository snapshots;
	@Autowired VillageHistoryRepository histories;
	@Autowired CharacterRepository characters;
	@Autowired UserRepository users;

	private Character character;

	@BeforeEach
	void setUp() {
		clean();
		User user = users.save(new User("npc-dialogue@example.com", "password", "대화"));
		character = characters.save(Character.create(user, "에덴", CharacterGender.NONE,
				HairStyle.PIXEL_CUT, "brown", Outfit.ROBE, CharacterJob.WIZARD));
		snapshots.save(VillageThemeSnapshot.create(character, VillageTheme.BLOOMING_VILLAGE,
				VillageCategory.NATURE, null, LocalDateTime.now()));
	}

	@AfterEach
	void tearDown() {
		clean();
	}

	@Test
	void getDialogueRecordsFirstInteraction() {
		var result = dialogueService.getDialogue(character.getId(), 10L);
		var memory = npcMemories.findByCharacterIdAndNpcId(character.getId(), 10L).orElseThrow();

		assertEquals(NpcDialogueKey.FIRST_MEETING, result.dialogueKey());
		assertTrue(result.memoryChanged());
		assertEquals(1, memory.getInteractionCount());
		assertEquals(NpcDialogueKey.FIRST_MEETING.name(), memory.getLastDialogueKey());
		assertEquals(VillageTheme.BLOOMING_VILLAGE, memory.getRememberedTheme());
		assertEquals(VillageCategory.NATURE, memory.getRememberedCategory());
		assertNotNull(memory.getLastInteractedAt());
	}

	@Test
	void secondDialogueUsesThemeAndIncrementsInteractionCount() {
		dialogueService.getDialogue(character.getId(), 10L);

		var result = dialogueService.getDialogue(character.getId(), 10L);
		var memory = npcMemories.findByCharacterIdAndNpcId(character.getId(), 10L).orElseThrow();

		assertEquals(NpcDialogueKey.BLOOMING_FIRST, result.dialogueKey());
		assertEquals(2, memory.getInteractionCount());
		assertEquals(NpcDialogueKey.BLOOMING_FIRST.name(), memory.getLastDialogueKey());
	}

	@Test
	void repeatedDialogueAlternatesDeterministically() {
		dialogueService.getDialogue(character.getId(), 10L);
		dialogueService.getDialogue(character.getId(), 10L);
		dialogueService.getDialogue(character.getId(), 10L);

		var alt = dialogueService.getDialogue(character.getId(), 10L);
		var returning = dialogueService.getDialogue(character.getId(), 10L);

		assertEquals(NpcDialogueKey.BLOOMING_REPEAT_ALT, alt.dialogueKey());
		assertEquals(NpcDialogueKey.BLOOMING_RETURNING, returning.dialogueKey());
		assertEquals(5, npcMemories.findByCharacterIdAndNpcId(character.getId(), 10L)
				.orElseThrow().getInteractionCount());
	}

	private void clean() {
		npcMemories.deleteAll();
		histories.deleteAll();
		snapshots.deleteAll();
		characters.deleteAll();
		users.deleteAll();
	}
}
