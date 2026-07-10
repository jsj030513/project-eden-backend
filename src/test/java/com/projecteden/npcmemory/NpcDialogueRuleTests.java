package com.projecteden.npcmemory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.projecteden.npcmemory.context.NpcContext;
import com.projecteden.npcmemory.dialogue.NpcDialogueKey;
import com.projecteden.npcmemory.dialogue.NpcDialogueRule;
import com.projecteden.village.domain.VillageCategory;
import com.projecteden.village.domain.VillageHistoryType;
import com.projecteden.village.domain.VillageTheme;

class NpcDialogueRuleTests {

	private final NpcDialogueRule rule = new NpcDialogueRule();

	@Test
	void firstMeetingAlwaysUsesFirstMeeting() {
		var result = rule.selectDialogue(context(
				VillageTheme.BLOOMING_VILLAGE,
				VillageCategory.NATURE,
				null,
				null,
				null,
				0,
				null,
				true));

		assertEquals(NpcDialogueKey.FIRST_MEETING, result.dialogueKey());
		assertEquals("처음 보는 풍경인데도 이상하게 따뜻하네요.", result.message());
	}

	@Test
	void secondMeetingUsesThemeFirstDialogue() {
		var result = rule.selectDialogue(context(
				VillageTheme.BLOOMING_VILLAGE,
				VillageCategory.NATURE,
				null,
				null,
				null,
				1,
				NpcDialogueKey.FIRST_MEETING.name(),
				false));

		assertEquals(NpcDialogueKey.BLOOMING_FIRST, result.dialogueKey());
		assertEquals(VillageCategory.NATURE, result.rememberedCategory());
	}

	@Test
	void bloomingReturningDialogueIsSelected() {
		var result = rule.selectDialogue(context(
				VillageTheme.BLOOMING_VILLAGE,
				VillageCategory.NATURE,
				null,
				null,
				null,
				2,
				NpcDialogueKey.BLOOMING_FIRST.name(),
				false));

		assertEquals(NpcDialogueKey.BLOOMING_RETURNING, result.dialogueKey());
	}

	@Test
	void sameReturningKeyUsesRepeatAlt() {
		var result = rule.selectDialogue(context(
				VillageTheme.BLOOMING_VILLAGE,
				VillageCategory.NATURE,
				null,
				null,
				null,
				3,
				NpcDialogueKey.BLOOMING_RETURNING.name(),
				false));

		assertEquals(NpcDialogueKey.BLOOMING_REPEAT_ALT, result.dialogueKey());
	}

	@Test
	void repeatAltSwitchesBackToReturning() {
		var result = rule.selectDialogue(context(
				VillageTheme.BLOOMING_VILLAGE,
				VillageCategory.NATURE,
				null,
				null,
				null,
				4,
				NpcDialogueKey.BLOOMING_REPEAT_ALT.name(),
				false));

		assertEquals(NpcDialogueKey.BLOOMING_RETURNING, result.dialogueKey());
	}

	@Test
	void warmDialogueIsSelected() {
		assertEquals(NpcDialogueKey.WARM_FIRST, themeFirst(VillageTheme.WARM_VILLAGE));
	}

	@Test
	void walkingDialogueIsSelected() {
		assertEquals(NpcDialogueKey.WALKING_FIRST, themeFirst(VillageTheme.WALKING_VILLAGE));
	}

	@Test
	void watersideDialogueIsSelected() {
		assertEquals(NpcDialogueKey.WATERSIDE_FIRST, themeFirst(VillageTheme.WATERSIDE_VILLAGE));
	}

	@Test
	void animalDialogueIsSelected() {
		assertEquals(NpcDialogueKey.ANIMAL_FIRST, themeFirst(VillageTheme.ANIMAL_FRIENDLY_VILLAGE));
	}

	@Test
	void quietDialogueIsSelected() {
		assertEquals(NpcDialogueKey.QUIET_FIRST, themeFirst(VillageTheme.QUIET_VILLAGE));
	}

	@Test
	void undefinedDialogueIsSelected() {
		assertEquals(NpcDialogueKey.UNDEFINED_FIRST, themeFirst(VillageTheme.UNDEFINED));
	}

	@Test
	void recentThemeChangeHasPriority() {
		var result = rule.selectDialogue(context(
				VillageTheme.BLOOMING_VILLAGE,
				VillageCategory.NATURE,
				VillageHistoryType.THEME_CHANGED,
				VillageCategory.FOOD,
				null,
				2,
				NpcDialogueKey.BLOOMING_FIRST.name(),
				false));

		assertEquals(NpcDialogueKey.RECENT_THEME_CHANGE, result.dialogueKey());
		assertEquals(VillageCategory.FOOD, result.rememberedCategory());
	}

	@Test
	void recentChangeAppearedHasPriority() {
		var result = rule.selectDialogue(context(
				VillageTheme.BLOOMING_VILLAGE,
				VillageCategory.NATURE,
				VillageHistoryType.CHANGE_APPEARED,
				VillageCategory.WATER,
				null,
				2,
				null,
				false));

		assertEquals(NpcDialogueKey.RECENT_CHANGE_APPEARED, result.dialogueKey());
	}

	@Test
	void recentMemoryRecordedHasPriority() {
		var result = rule.selectDialogue(context(
				VillageTheme.BLOOMING_VILLAGE,
				VillageCategory.NATURE,
				VillageHistoryType.MEMORY_RECORDED,
				VillageCategory.ANIMAL,
				null,
				2,
				null,
				false));

		assertEquals(NpcDialogueKey.RECENT_MEMORY_RECORDED, result.dialogueKey());
	}

	@Test
	void rememberedCategoryFallsBackToExistingMemoryCategory() {
		var result = rule.selectDialogue(context(
				VillageTheme.UNDEFINED,
				null,
				null,
				null,
				VillageCategory.WALK,
				2,
				null,
				false));

		assertEquals(VillageCategory.WALK, result.rememberedCategory());
	}

	private NpcDialogueKey themeFirst(VillageTheme theme) {
		return rule.selectDialogue(context(
				theme,
				VillageCategory.NATURE,
				null,
				null,
				null,
				1,
				NpcDialogueKey.FIRST_MEETING.name(),
				false)).dialogueKey();
	}

	private NpcContext context(
			VillageTheme theme,
			VillageCategory primaryCategory,
			VillageHistoryType historyType,
			VillageCategory historyCategory,
			VillageCategory rememberedCategory,
			int interactionCount,
			String lastDialogueKey,
			boolean firstMeeting) {
		return new NpcContext(
				1L,
				10L,
				theme,
				primaryCategory,
				null,
				historyType,
				historyCategory,
				rememberedCategory,
				interactionCount,
				lastDialogueKey,
				firstMeeting,
				null);
	}
}
