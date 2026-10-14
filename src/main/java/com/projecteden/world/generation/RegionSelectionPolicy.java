package com.projecteden.world.generation;

import com.projecteden.world.chunk.WorldChunkManager;
import com.projecteden.world.chunk.WorldChunkRegionType;
import com.projecteden.world.domain.World;
import org.springframework.stereotype.Component;

@Component
public class RegionSelectionPolicy {

    public WorldChunkRegionType select(World world, int chunkX, int chunkY) {
        if (WorldChunkManager.isHubChunk(chunkX, chunkY)) return WorldChunkRegionType.HUB;

        if (chunkX == -1 && (chunkY == 0 || chunkY == 1)) return WorldChunkRegionType.MEADOW;
        if (chunkX == 3 && (chunkY == 0 || chunkY == 1)) return WorldChunkRegionType.FOREST;
        if (chunkX == 1 && chunkY == 2) return WorldChunkRegionType.POND;
        if (chunkX == 1 && chunkY == -1) return WorldChunkRegionType.MEADOW;
        if (chunkX == 0 && chunkY == -1) return WorldChunkRegionType.FOREST;
        if (chunkX == 2 && chunkY == -1) return WorldChunkRegionType.POND;

        long mixed = mix(world.getSeed(), chunkX, chunkY, world.getWorldGenerationVersion());
        WorldChunkRegionType[] options = {
                WorldChunkRegionType.MEADOW,
                WorldChunkRegionType.FOREST,
                WorldChunkRegionType.POND
        };
        return options[Math.floorMod(mixed, options.length)];
    }

    static long mix(long seed, int chunkX, int chunkY, int version) {
        long value = seed ^ 0x9E3779B97F4A7C15L;
        value ^= (long) chunkX * 0xBF58476D1CE4E5B9L;
        value ^= (long) chunkY * 0x94D049BB133111EBL;
        value ^= (long) version * 0xD6E8FEB86659FD93L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
