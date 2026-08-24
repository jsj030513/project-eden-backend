package com.projecteden.world.npc;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NpcAffinityStateRepository extends JpaRepository<NpcAffinityState, Long> {
    Optional<NpcAffinityState> findByCharacterIdAndNpcObjectId(Long characterId, Long npcObjectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select affinity from NpcAffinityState affinity
            where affinity.character.id = :characterId
              and affinity.npcObject.id = :npcObjectId
            """)
    Optional<NpcAffinityState> findForUpdate(
            @Param("characterId") Long characterId,
            @Param("npcObjectId") Long npcObjectId);

    @Query("""
            select affinity from NpcAffinityState affinity
            join fetch affinity.npcObject
            where affinity.character.id = :characterId
            order by affinity.npcObject.id
            """)
    List<NpcAffinityState> findAllForCharacter(@Param("characterId") Long characterId);
}
