package com.projecteden.world.ecology;

import com.projecteden.world.chunk.WorldChunkRegionType;

public record MoveResponse(
        boolean accepted,
        int currentX,
        int currentY,
        TerrainType terrainType,
        String reason,
        boolean enteredChunk,
        Integer chunkX,
        Integer chunkY,
        boolean newlyDiscovered,
        WorldChunkRegionType regionType,
        String regionDisplayKey) {

    public MoveResponse(
            boolean accepted,
            int currentX,
            int currentY,
            TerrainType terrainType,
            String reason) {
        this(accepted, currentX, currentY, terrainType, reason,
                false, null, null, false, null, null);
    }
}
