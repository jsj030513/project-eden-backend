package com.projecteden.npcmemory.dialogue;

import org.springframework.stereotype.Component;

import com.projecteden.npcmemory.context.NpcContext;
import com.projecteden.village.domain.VillageCategory;
import com.projecteden.village.domain.VillageHistoryType;
import com.projecteden.village.domain.VillageTheme;

@Component
public class NpcDialogueRule {

	public NpcDialogueResult selectDialogue(NpcContext context) {
		if (context.firstMeeting()) {
			return result(
					NpcDialogueKey.FIRST_MEETING,
					"처음 보는 풍경인데도 이상하게 따뜻하네요.",
					context,
					context.primaryCategory());
		}

		if (context.recentHistoryType() == VillageHistoryType.THEME_CHANGED) {
			return recentResult(
					NpcDialogueKey.RECENT_THEME_CHANGE,
					"마을의 공기가 조금 달라진 것 같아요.",
					context);
		}

		if (context.recentHistoryType() == VillageHistoryType.CHANGE_APPEARED) {
			return recentResult(
					NpcDialogueKey.RECENT_CHANGE_APPEARED,
					"새로운 풍경이 조용히 자리를 잡았네요.",
					context);
		}

		if (context.recentHistoryType() == VillageHistoryType.MEMORY_RECORDED) {
			return recentResult(
					NpcDialogueKey.RECENT_MEMORY_RECORDED,
					"오늘도 작은 흔적 하나가 마을에 머물렀어요.",
					context);
		}

		return switch (context.currentTheme()) {
			case BLOOMING_VILLAGE -> themeResult(
					context,
					NpcDialogueKey.BLOOMING_FIRST,
					NpcDialogueKey.BLOOMING_RETURNING,
					NpcDialogueKey.BLOOMING_REPEAT_ALT,
					"꽃이 이 길을 오래 바라보고 있나 봐요.",
					"꽃이 이 길을 오래 기억하고 있나 봐요.",
					"바람이 꽃 사이를 천천히 지나가고 있어요.");
			case WARM_VILLAGE -> themeResult(
					context,
					NpcDialogueKey.WARM_FIRST,
					NpcDialogueKey.WARM_RETURNING,
					NpcDialogueKey.WARM_REPEAT_ALT,
					"따뜻한 불빛이 마을 곳곳에 머물고 있네요.",
					"따뜻한 식탁의 기억이 예전보다 오래 남아 있어요.",
					"저녁이 오면 작은 불빛들이 하나둘 켜져요.");
			case WALKING_VILLAGE -> themeResult(
					context,
					NpcDialogueKey.WALKING_FIRST,
					NpcDialogueKey.WALKING_RETURNING,
					NpcDialogueKey.WALKING_REPEAT_ALT,
					"길이 조금씩 멀리 이어지고 있네요.",
					"예전보다 발자국이 더 오래 남는 것 같아요.",
					"오늘은 길 끝까지 천천히 걸어보고 싶네요.");
			case WATERSIDE_VILLAGE -> themeResult(
					context,
					NpcDialogueKey.WATERSIDE_FIRST,
					NpcDialogueKey.WATERSIDE_RETURNING,
					NpcDialogueKey.WATERSIDE_REPEAT_ALT,
					"물가의 바람이 마을에 오래 머물고 있어요.",
					"강가의 소리가 전보다 더 가까워진 것 같아요.",
					"물결이 같은 자리를 천천히 지나가고 있어요.");
			case ANIMAL_FRIENDLY_VILLAGE -> themeResult(
					context,
					NpcDialogueKey.ANIMAL_FIRST,
					NpcDialogueKey.ANIMAL_RETURNING,
					NpcDialogueKey.ANIMAL_REPEAT_ALT,
					"작은 발자국이 이 근처를 지나간 것 같아요.",
					"작은 친구들이 이 길을 익숙하게 기억하나 봐요.",
					"새들이 머무를 자리가 조금 더 늘었네요.");
			case QUIET_VILLAGE -> themeResult(
					context,
					NpcDialogueKey.QUIET_FIRST,
					NpcDialogueKey.QUIET_RETURNING,
					NpcDialogueKey.QUIET_REPEAT_ALT,
					"조용한 풍경이 이곳에 천천히 머물고 있어요.",
					"말없이 남은 순간들이 이 마을을 채우고 있네요.",
					"오늘은 바람 소리도 조금 더 낮게 들려요.");
			case UNDEFINED -> undefinedResult(context);
		};
	}

	private NpcDialogueResult recentResult(
			NpcDialogueKey key, String message, NpcContext context) {
		return result(key, message, context, rememberedCategory(context));
	}

	private NpcDialogueResult themeResult(
			NpcContext context,
			NpcDialogueKey firstKey,
			NpcDialogueKey returningKey,
			NpcDialogueKey repeatAltKey,
			String firstMessage,
			String returningMessage,
			String repeatAltMessage) {
		if (context.interactionCount() == 1) {
			return result(firstKey, firstMessage, context, rememberedCategory(context));
		}

		NpcDialogueKey lastKey = parseLastKey(context.lastDialogueKey());
		if (lastKey == returningKey) {
			return result(repeatAltKey, repeatAltMessage, context, rememberedCategory(context));
		}
		return result(returningKey, returningMessage, context, rememberedCategory(context));
	}

	private NpcDialogueResult undefinedResult(NpcContext context) {
		if (context.interactionCount() == 1) {
			return result(
					NpcDialogueKey.UNDEFINED_FIRST,
					"아직 마을은 당신의 첫 순간을 기다리고 있어요.",
					context,
					rememberedCategory(context));
		}

		NpcDialogueKey lastKey = parseLastKey(context.lastDialogueKey());
		if (lastKey == NpcDialogueKey.UNDEFINED_RETURNING) {
			return result(
					NpcDialogueKey.UNDEFINED_FIRST,
					"아직 마을은 당신의 첫 순간을 기다리고 있어요.",
					context,
					rememberedCategory(context));
		}
		return result(
				NpcDialogueKey.UNDEFINED_RETURNING,
				"오늘은 어떤 풍경이 이곳에 머물게 될까요?",
				context,
				rememberedCategory(context));
	}

	private VillageCategory rememberedCategory(NpcContext context) {
		if (context.recentHistoryCategory() != null) {
			return context.recentHistoryCategory();
		}
		if (context.primaryCategory() != null) {
			return context.primaryCategory();
		}
		return context.rememberedCategory();
	}

	private NpcDialogueResult result(
			NpcDialogueKey key,
			String message,
			NpcContext context,
			VillageCategory rememberedCategory) {
		return new NpcDialogueResult(
				key,
				message,
				context.currentTheme(),
				rememberedCategory,
				false);
	}

	private NpcDialogueKey parseLastKey(String lastDialogueKey) {
		if (lastDialogueKey == null) {
			return null;
		}
		try {
			return NpcDialogueKey.valueOf(lastDialogueKey);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}
}
