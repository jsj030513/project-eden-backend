package com.projecteden.world.chunk;

import com.projecteden.world.domain.World;
import com.projecteden.world.ecology.WorldCoordinates;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorldChunkManager {

    public static final int HUB_MIN_CHUNK_X = 0;
    public static final int HUB_MAX_CHUNK_X = 2;
    public static final int HUB_MIN_CHUNK_Y = 0;
    public static final int HUB_MAX_CHUNK_Y = 1;

    private final WorldChunkRepository chunks;

    public WorldChunkManager(WorldChunkRepository chunks) {
        this.chunks = chunks;
    }

    @Transactional
    public List<WorldChunk> ensureHubChunks(World world) {
        if (world.getId() == null) return List.of();
        Set<String> existing = new HashSet<>();
        chunks.findByWorldIdOrderByChunkYAscChunkXAsc(world.getId())
                .forEach(chunk -> existing.add(key(chunk.getChunkX(), chunk.getChunkY())));
        for (int chunkY = HUB_MIN_CHUNK_Y; chunkY <= HUB_MAX_CHUNK_Y; chunkY++) {
            for (int chunkX = HUB_MIN_CHUNK_X; chunkX <= HUB_MAX_CHUNK_X; chunkX++) {
                if (existing.contains(key(chunkX, chunkY))) continue;
                chunks.save(WorldChunk.hub(world, chunkX, chunkY));
            }
        }
        return chunks.findByWorldIdOrderByChunkYAscChunkXAsc(world.getId());
    }

    @Transactional(readOnly = true)
    public boolean hasCanonicalHub(World world) {
        return world.getId() != null && chunks.countByWorldId(world.getId()) >= 6;
    }

    @Transactional(readOnly = true)
    public boolean isAvailable(World world, int tileX, int tileY) {
        return chunks.findByWorldIdAndChunkXAndChunkY(
                world.getId(),
                WorldCoordinates.tileToChunk(tileX),
                WorldCoordinates.tileToChunk(tileY))
                .filter(chunk -> chunk.getStatus() == WorldChunkStatus.GENERATED)
                .isPresent();
    }

    public static boolean isHubChunk(int chunkX, int chunkY) {
        return chunkX >= HUB_MIN_CHUNK_X && chunkX <= HUB_MAX_CHUNK_X
                && chunkY >= HUB_MIN_CHUNK_Y && chunkY <= HUB_MAX_CHUNK_Y;
    }

    private static String key(int chunkX, int chunkY) {
        return chunkX + ":" + chunkY;
    }
}
