package com.projecteden.world.npc;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NpcQuestStateRepository extends JpaRepository<NpcQuestState, Long> {
    List<NpcQuestState> findByCharacterIdOrderByQuestIdAsc(Long characterId);
    Optional<NpcQuestState> findByCharacterIdAndQuestId(Long characterId, String questId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select state from NpcQuestState state
            where state.character.id = :characterId and state.questId = :questId
            """)
    Optional<NpcQuestState> findForUpdate(
            @Param("characterId") Long characterId,
            @Param("questId") String questId);
}
