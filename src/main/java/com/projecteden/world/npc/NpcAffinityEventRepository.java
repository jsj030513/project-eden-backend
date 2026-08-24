package com.projecteden.world.npc;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NpcAffinityEventRepository extends JpaRepository<NpcAffinityEvent, Long> {
    boolean existsByCharacterIdAndNpcObjectIdAndEventKey(
            Long characterId, Long npcObjectId, String eventKey);

    long countByCharacterIdAndNpcObjectIdAndDialogueKey(
            Long characterId, Long npcObjectId, String dialogueKey);

    boolean existsByCharacterIdAndNpcObjectIdAndCreatedAtBetween(
            Long characterId, Long npcObjectId, LocalDateTime start, LocalDateTime end);

    long countByCharacterId(Long characterId);
}
