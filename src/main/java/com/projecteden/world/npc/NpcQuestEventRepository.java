package com.projecteden.world.npc;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NpcQuestEventRepository extends JpaRepository<NpcQuestEvent, Long> {
    boolean existsByCharacterIdAndEventKey(Long characterId, String eventKey);
    long countByCharacterId(Long characterId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event from NpcQuestEvent event
            where event.character.id = :characterId
              and event.world.id = :worldId
              and event.processingStatus in :statuses
            order by event.createdAt, event.id
            """)
    List<NpcQuestEvent> findReplayBatchForUpdate(
            @Param("characterId") Long characterId,
            @Param("worldId") Long worldId,
            @Param("statuses") Collection<NpcQuestEventProcessingStatus> statuses,
            Pageable pageable);

    List<NpcQuestEvent> findByCharacterIdOrderByCreatedAtAscIdAsc(Long characterId);
}
