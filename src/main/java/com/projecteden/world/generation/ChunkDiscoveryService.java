package com.projecteden.world.generation;

import com.projecteden.world.chunk.WorldChunk;
import com.projecteden.world.chunk.WorldChunkRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChunkDiscoveryService {

    private final WorldChunkRepository chunks;
    private final Clock clock;

    public ChunkDiscoveryService(WorldChunkRepository chunks, Clock clock) {
        this.chunks = chunks;
        this.clock = clock;
    }

    @Transactional
    public DiscoveryResult discover(Long worldId, int chunkX, int chunkY) {
        WorldChunk chunk = chunks.findForUpdate(worldId, chunkX, chunkY)
                .orElseThrow(() -> new IllegalStateException("CHUNK_UNAVAILABLE"));
        boolean newlyDiscovered = chunk.discover(LocalDateTime.now(clock));
        return new DiscoveryResult(chunk, newlyDiscovered);
    }

    public record DiscoveryResult(WorldChunk chunk, boolean newlyDiscovered) {
    }
}
