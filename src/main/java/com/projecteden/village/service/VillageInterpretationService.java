package com.projecteden.village.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.character.domain.Character;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.village.domain.VillageCategory;
import com.projecteden.village.domain.VillageHistory;
import com.projecteden.village.domain.VillageHistoryType;
import com.projecteden.village.domain.VillageMemory;
import com.projecteden.village.domain.VillageTheme;
import com.projecteden.village.domain.VillageThemeSnapshot;
import com.projecteden.village.dto.VillageExpressionResponse;
import com.projecteden.village.dto.VillageInterpretationResponse;
import com.projecteden.village.repository.VillageHistoryRepository;
import com.projecteden.village.repository.VillageMemoryRepository;
import com.projecteden.village.repository.VillageThemeSnapshotRepository;

@Service
public class VillageInterpretationService {

	private static final int THEME_CHANGE_MARGIN = 3;

	private final VillageMemoryRepository memoryRepository;
	private final VillageThemeSnapshotRepository snapshotRepository;
	private final VillageHistoryRepository historyRepository;
	private final CharacterRepository characterRepository;
	private final Clock clock;

	public VillageInterpretationService(
			VillageMemoryRepository memoryRepository,
			VillageThemeSnapshotRepository snapshotRepository,
			VillageHistoryRepository historyRepository,
			CharacterRepository characterRepository,
			Clock clock) {
		this.memoryRepository = memoryRepository;
		this.snapshotRepository = snapshotRepository;
		this.historyRepository = historyRepository;
		this.characterRepository = characterRepository;
		this.clock = clock;
	}

	@Transactional
	public VillageInterpretationResponse interpretAndUpdateTheme(Long characterId) {
		Character character = findCharacter(characterId);
		List<VillageMemory> memories = sortedMemories(characterId);
		Interpretation interpretation = interpret(memories);
		LocalDateTime now = LocalDateTime.now(clock);

		VillageThemeSnapshot snapshot = snapshotRepository.findByCharacterId(characterId)
				.orElseGet(() -> snapshotRepository.save(VillageThemeSnapshot.create(
						character,
						interpretation.theme(),
						interpretation.primaryCategory(),
						interpretation.secondaryCategory(),
						now)));

		if (snapshot.getTheme() == interpretation.theme()) {
			snapshot.updateCategories(
					interpretation.primaryCategory(), interpretation.secondaryCategory());
		} else if (shouldChangeTheme(snapshot, interpretation, memories)) {
			snapshot.changeTheme(
					interpretation.theme(),
					interpretation.primaryCategory(),
					interpretation.secondaryCategory(),
					now);
			historyRepository.save(VillageHistory.create(
					character,
					VillageHistoryType.THEME_CHANGED,
					interpretation.primaryCategory(),
					null,
					messageFor(interpretation.theme()),
					now));
		} else {
			snapshot.updateSecondaryCategory(interpretation.secondaryCategory());
		}

		return toResponse(snapshot);
	}

	@Transactional
	public VillageInterpretationResponse getInterpretation(Long characterId) {
		findCharacter(characterId);
		return snapshotRepository.findByCharacterId(characterId)
				.map(this::toResponse)
				.orElseGet(() -> interpretAndUpdateTheme(characterId));
	}

	@Transactional
	public VillageInterpretationResponse getInterpretationByUserId(Long userId) {
		Character character = characterRepository.findByUserId(userId)
				.orElseThrow(() -> new ResourceNotFoundException("캐릭터를 찾을 수 없습니다."));
		return getInterpretation(character.getId());
	}

	private Interpretation interpret(List<VillageMemory> memories) {
		if (memories.isEmpty()) {
			return new Interpretation(VillageTheme.UNDEFINED, null, null, 0);
		}
		VillageMemory primary = memories.getFirst();
		VillageCategory secondary = memories.size() > 1
				? memories.get(1).getCategory()
				: null;
		return new Interpretation(
				themeFor(primary.getCategory()),
				primary.getCategory(),
				secondary,
				primary.getMemoryCount());
	}

	private List<VillageMemory> sortedMemories(Long characterId) {
		return memoryRepository.findByCharacterId(characterId).stream()
				.sorted(Comparator.comparingInt(VillageMemory::getMemoryCount).reversed()
						.thenComparingInt(memory -> priority(memory.getCategory())))
				.toList();
	}

	private boolean shouldChangeTheme(
			VillageThemeSnapshot snapshot,
			Interpretation interpretation,
			List<VillageMemory> memories) {
		if (snapshot.getTheme() == VillageTheme.UNDEFINED
				|| snapshot.getPrimaryCategory() == null) {
			return true;
		}
		int currentCount = memories.stream()
				.filter(memory -> memory.getCategory() == snapshot.getPrimaryCategory())
				.mapToInt(VillageMemory::getMemoryCount)
				.findFirst()
				.orElse(-1);
		if (currentCount < 0) {
			return true;
		}
		return interpretation.primaryCount() - currentCount >= THEME_CHANGE_MARGIN;
	}

	private VillageTheme themeFor(VillageCategory category) {
		return switch (category) {
			case NATURE -> VillageTheme.BLOOMING_VILLAGE;
			case FOOD -> VillageTheme.WARM_VILLAGE;
			case WALK -> VillageTheme.WALKING_VILLAGE;
			case WATER -> VillageTheme.WATERSIDE_VILLAGE;
			case ANIMAL -> VillageTheme.ANIMAL_FRIENDLY_VILLAGE;
			case STUDY -> VillageTheme.QUIET_VILLAGE;
			case WORK -> VillageTheme.WARM_VILLAGE;
			case UNKNOWN -> VillageTheme.QUIET_VILLAGE;
		};
	}

	private int priority(VillageCategory category) {
		return switch (category) {
			case NATURE -> 0;
			case FOOD -> 1;
			case WALK -> 2;
			case WATER -> 3;
			case ANIMAL -> 4;
			case STUDY -> 5;
			case WORK -> 6;
			case UNKNOWN -> 7;
		};
	}

	private VillageInterpretationResponse toResponse(VillageThemeSnapshot snapshot) {
		return new VillageInterpretationResponse(
				snapshot.getTheme(),
				snapshot.getPrimaryCategory(),
				snapshot.getSecondaryCategory(),
				messageFor(snapshot.getTheme()),
				expressionsFor(snapshot.getTheme()),
				snapshot.getAppliedAt(),
				snapshot.getRuleVersion());
	}

	private String messageFor(VillageTheme theme) {
		return switch (theme) {
			case BLOOMING_VILLAGE -> "이 마을은 꽃과 바람이 오래 머무는 곳이 되어가고 있습니다.";
			case WARM_VILLAGE -> "따뜻한 식탁의 기억이 마을 곳곳에 남아 있습니다.";
			case WALKING_VILLAGE -> "조용한 길이 조금씩 이어지고 있습니다.";
			case WATERSIDE_VILLAGE -> "물가의 바람이 마을에 오래 머물기 시작했습니다.";
			case ANIMAL_FRIENDLY_VILLAGE -> "작은 발자국들이 마을에 생기를 더하고 있습니다.";
			case QUIET_VILLAGE -> "말없이 남은 순간들이 조용한 풍경이 되고 있습니다.";
			case UNDEFINED -> "아직 마을은 당신의 첫 순간을 기다리고 있습니다.";
		};
	}

	private List<VillageExpressionResponse> expressionsFor(VillageTheme theme) {
		return switch (theme) {
			case BLOOMING_VILLAGE -> List.of(
					new VillageExpressionResponse("NPC_DIALOGUE", "꽃이 이 마을을 참 좋아하는 것 같네요.", "flower"),
					new VillageExpressionResponse("SCENERY_HINT", "부드러운 꽃길이 마을에 더 오래 머뭅니다.", "flower_path"));
			case WARM_VILLAGE -> List.of(
					new VillageExpressionResponse("NPC_DIALOGUE", "마을 어딘가에서 좋은 냄새가 머물고 있어요.", "table"),
					new VillageExpressionResponse("SCENERY_HINT", "작은 불빛이 저녁까지 천천히 남아 있습니다.", "warm_light"));
			case WALKING_VILLAGE -> List.of(
					new VillageExpressionResponse("NPC_DIALOGUE", "오늘은 길이 조금 더 멀리 이어진 것 같네요.", "path"),
					new VillageExpressionResponse("SCENERY_HINT", "작은 발자국이 산책길에 조용히 남아 있습니다.", "walking_path"));
			case WATERSIDE_VILLAGE -> List.of(
					new VillageExpressionResponse("NPC_DIALOGUE", "강가의 바람이 부드럽게 지나갑니다.", "water"),
					new VillageExpressionResponse("SCENERY_HINT", "물가에 조용한 길이 생겼습니다.", "waterside_path"));
			case ANIMAL_FRIENDLY_VILLAGE -> List.of(
					new VillageExpressionResponse("NPC_DIALOGUE", "새들이 머무를 작은 자리가 생겼습니다.", "animal"),
					new VillageExpressionResponse("SCENERY_HINT", "조용한 쉼터에 생기가 머물고 있어요.", "shelter"));
			case QUIET_VILLAGE -> List.of(
					new VillageExpressionResponse("NPC_DIALOGUE", "아직 이름 붙이지 못한 풍경이 하나 머물렀습니다.", "quiet"),
					new VillageExpressionResponse("SCENERY_HINT", "조용한 자리가 마을 어딘가에 생겼습니다.", "quiet_place"));
			case UNDEFINED -> List.of();
		};
	}

	private Character findCharacter(Long id) {
		return characterRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("캐릭터를 찾을 수 없습니다."));
	}

	private record Interpretation(
			VillageTheme theme,
			VillageCategory primaryCategory,
			VillageCategory secondaryCategory,
			int primaryCount) {
	}
}
