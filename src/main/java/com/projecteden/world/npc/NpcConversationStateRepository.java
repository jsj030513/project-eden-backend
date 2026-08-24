package com.projecteden.world.npc;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NpcConversationStateRepository extends JpaRepository<NpcConversationState, Long> {
    Optional<NpcConversationState> findByCharacterIdAndNpcObjectId(Long characterId, Long npcObjectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select state from NpcConversationState state
            where state.character.id = :characterId and state.npcObject.id = :npcObjectId
            """)
    Optional<NpcConversationState> findForUpdate(
            @Param("characterId") Long characterId,
            @Param("npcObjectId") Long npcObjectId);
}
