package com.projecteden.world.chunk;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorldChunkRepository extends JpaRepository<WorldChunk, Long> {
    long countByWorldId(Long worldId);
    List<WorldChunk> findByWorldIdOrderByChunkYAscChunkXAsc(Long worldId);
    List<WorldChunk> findByWorldIdAndChunkXBetweenAndChunkYBetweenOrderByChunkYAscChunkXAsc(
            Long worldId, int minChunkX, int maxChunkX, int minChunkY, int maxChunkY);
    Optional<WorldChunk> findByWorldIdAndChunkXAndChunkY(Long worldId, int chunkX, int chunkY);

    @Query("""
            select chunk from WorldChunk chunk
            where chunk.world.id = :worldId
              and chunk.status = com.projecteden.world.chunk.WorldChunkStatus.GENERATED
              and chunk.discoveredAt is not null
            order by chunk.chunkY, chunk.chunkX
            """)
    List<WorldChunk> findGeneratedDiscovered(@Param("worldId") Long worldId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select chunk from WorldChunk chunk
            where chunk.world.id = :worldId and chunk.chunkX = :chunkX and chunk.chunkY = :chunkY
            """)
    Optional<WorldChunk> findForUpdate(
            @Param("worldId") Long worldId,
            @Param("chunkX") int chunkX,
            @Param("chunkY") int chunkY);
}
