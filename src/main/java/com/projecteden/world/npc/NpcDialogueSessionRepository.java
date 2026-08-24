package com.projecteden.world.npc;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NpcDialogueSessionRepository extends JpaRepository<NpcDialogueSession, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select session from NpcDialogueSession session
            join fetch session.npcObject object
            join fetch session.character character
            where session.id = :id
            """)
    Optional<NpcDialogueSession> findByIdForUpdate(@Param("id") String id);

    Optional<NpcDialogueSession> findFirstByCharacterIdAndNpcObjectIdAndCompletedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            Long characterId,
            Long npcObjectId,
            LocalDateTime now);

    boolean existsByNpcObjectIdAndCompletedFalseAndExpiresAtAfter(Long npcObjectId, LocalDateTime now);

    @Query("""
            select session.npcObject.id
            from NpcDialogueSession session
            where session.world.id = :worldId
              and session.completed = false
              and session.expiresAt > :now
            """)
    List<Long> findActiveNpcObjectIds(
            @Param("worldId") Long worldId,
            @Param("now") LocalDateTime now);
}
