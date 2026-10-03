package com.projecteden.npcmemory.context;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.npcmemory.domain.NpcMemory;
import com.projecteden.npcmemory.repository.NpcMemoryRepository;
import com.projecteden.village.domain.VillageHistory;
import com.projecteden.village.domain.VillageTheme;
import com.projecteden.village.repository.VillageHistoryRepository;
import com.projecteden.village.repository.VillageThemeSnapshotRepository;

@Component
public class NpcContextProvider {

	public static final Duration RECENT_HISTORY_WINDOW = Duration.ofHours(24);

	private final VillageThemeSnapshotRepository snapshots;
	private final VillageHistoryRepository histories;
	private final NpcMemoryRepository npcMemories;

	public NpcContextProvider(
			VillageThemeSnapshotRepository snapshots,
			VillageHistoryRepository histories,
			NpcMemoryRepository npcMemories) {
		this.snapshots = snapshots;
		this.histories = histories;
		this.npcMemories = npcMemories;
	}

	@Transactional(readOnly = true)
	public NpcContext buildContext(Long characterId, Long npcId) {
		var snapshot = snapshots.findByCharacterId(characterId);
		var recentHistory = histories.findTopByCharacterIdOrderByIdDesc(characterId)
				.filter(this::isRecent);
		var memory = npcMemories.findByCharacterIdAndNpcId(characterId, npcId);

		return new NpcContext(
				characterId,
				npcId,
				snapshot.map(s -> s.getTheme()).orElse(VillageTheme.UNDEFINED),
				snapshot.map(s -> s.getPrimaryCategory()).orElse(null),
				snapshot.map(s -> s.getSecondaryCategory()).orElse(null),
				recentHistory.map(VillageHistory::getHistoryType).orElse(null),
				recentHistory.map(VillageHistory::getCategory).orElse(null),
				memory.map(NpcMemory::getRememberedCategory).orElse(null),
				memory.map(NpcMemory::getInteractionCount).orElse(0),
				memory.map(NpcMemory::getLastDialogueKey).orElse(null),
				memory.isEmpty() || memory.map(NpcMemory::getInteractionCount).orElse(0) == 0,
				memory.map(NpcMemory::getLastInteractedAt).orElse(null));
	}

	private boolean isRecent(VillageHistory history) {
		return !history.getCreatedAt().isBefore(
				LocalDateTime.now().minus(RECENT_HISTORY_WINDOW));
	}
}
