package com.projecteden.npcmemory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.projecteden.npcmemory.repository.NpcMemoryRepository;
import com.projecteden.npcmemory.service.NpcMemoryService;
import com.projecteden.village.domain.VillageCategory;
import com.projecteden.village.domain.VillageTheme;

@SpringBootTest
@ActiveProfiles("test")
class NpcMemoryServiceTests {

	@Autowired NpcMemoryService npcMemoryService;
	@Autowired NpcMemoryRepository npcMemories;

	@BeforeEach
	void setUp() {
		npcMemories.deleteAll();
	}

	@AfterEach
	void tearDown() {
		npcMemories.deleteAll();
	}

	@Test
	void getOrCreateMemoryCreatesMemoryOnFirstCall() {
		var memory = npcMemoryService.getOrCreateMemory(1L, 10L);

		assertNotNull(memory.getId());
		assertEquals(1L, memory.getCharacterId());
		assertEquals(10L, memory.getNpcId());
		assertEquals(0, memory.getInteractionCount());
		assertNull(memory.getRememberedTheme());
		assertNull(memory.getRememberedCategory());
		assertNull(memory.getLastDialogueKey());
		assertNull(memory.getLastInteractedAt());
		assertEquals(1, npcMemories.count());
	}

	@Test
	void getOrCreateMemoryDoesNotCreateDuplicateForSameCharacterAndNpc() {
		var first = npcMemoryService.getOrCreateMemory(1L, 10L);
		var second = npcMemoryService.getOrCreateMemory(1L, 10L);

		assertEquals(first.getId(), second.getId());
		assertEquals(1, npcMemories.count());
	}

	@Test
	void getMemoryReturnsExistingMemory() {
		var created = npcMemoryService.getOrCreateMemory(1L, 10L);

		var found = npcMemoryService.getMemory(1L, 10L);

		assertTrue(found.isPresent());
		assertEquals(created.getId(), found.orElseThrow().getId());
	}

	@Test
	void recordInteractionIncrementsCountAndUpdatesRememberedValues() {
		LocalDateTime before = LocalDateTime.now();

		var memory = npcMemoryService.recordInteraction(
				1L,
				10L,
				VillageTheme.BLOOMING_VILLAGE,
				VillageCategory.NATURE,
				"chief.flower.first");

		assertEquals(1, memory.getInteractionCount());
		assertEquals(VillageTheme.BLOOMING_VILLAGE, memory.getRememberedTheme());
		assertEquals(VillageCategory.NATURE, memory.getRememberedCategory());
		assertEquals("chief.flower.first", memory.getLastDialogueKey());
		assertNotNull(memory.getLastInteractedAt());
		assertTrue(!memory.getLastInteractedAt().isBefore(before));
	}

	@Test
	void recordInteractionUsesUndefinedWhenThemeIsNull() {
		var memory = npcMemoryService.recordInteraction(
				1L,
				10L,
				null,
				null,
				null);

		assertEquals(VillageTheme.UNDEFINED, memory.getRememberedTheme());
		assertNull(memory.getRememberedCategory());
		assertNull(memory.getLastDialogueKey());
		assertEquals(1, memory.getInteractionCount());
	}

	@Test
	void recordInteractionAccumulatesInteractionCount() {
		npcMemoryService.recordInteraction(
				1L,
				10L,
				VillageTheme.BLOOMING_VILLAGE,
				VillageCategory.NATURE,
				"chief.first");
		var memory = npcMemoryService.recordInteraction(
				1L,
				10L,
				VillageTheme.WALKING_VILLAGE,
				VillageCategory.WALK,
				"chief.walk");

		assertEquals(2, memory.getInteractionCount());
		assertEquals(VillageTheme.WALKING_VILLAGE, memory.getRememberedTheme());
		assertEquals(VillageCategory.WALK, memory.getRememberedCategory());
		assertEquals("chief.walk", memory.getLastDialogueKey());
		assertEquals(1, npcMemories.count());
	}

	@Test
	void differentNpcCreatesSeparateMemory() {
		var firstNpc = npcMemoryService.getOrCreateMemory(1L, 10L);
		var secondNpc = npcMemoryService.getOrCreateMemory(1L, 11L);

		assertEquals(2, npcMemories.count());
		assertEquals(10L, firstNpc.getNpcId());
		assertEquals(11L, secondNpc.getNpcId());
	}

	@Test
	void differentCharacterCreatesSeparateMemory() {
		var firstCharacter = npcMemoryService.getOrCreateMemory(1L, 10L);
		var secondCharacter = npcMemoryService.getOrCreateMemory(2L, 10L);

		assertEquals(2, npcMemories.count());
		assertEquals(1L, firstCharacter.getCharacterId());
		assertEquals(2L, secondCharacter.getCharacterId());
	}
}
