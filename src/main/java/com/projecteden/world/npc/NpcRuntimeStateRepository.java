package com.projecteden.world.npc;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NpcRuntimeStateRepository extends JpaRepository<NpcRuntimeState, Long> {
    long countByWorldId(Long worldId);
    Optional<NpcRuntimeState> findByNpcObjectId(Long npcObjectId);
    List<NpcRuntimeState> findByWorldIdOrderByNpcObjectIdAsc(Long worldId);

    @Query("""
            select state.world.id from NpcRuntimeState state
            where state.lastCheckpointAt is null or state.lastCheckpointAt <= :cutoff
            group by state.world.id
            order by min(state.lastCheckpointAt) asc nulls first, state.world.id
            """)
    List<Long> findDueWorldIds(
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select state from NpcRuntimeState state
            join fetch state.npcObject object
            where state.world.id = :worldId
            order by object.id
            """)
    List<NpcRuntimeState> findByWorldIdForUpdate(@Param("worldId") Long worldId);
}
