package com.projecteden.world.chunk;

public record ChunkWorldResponse(
        Long worldId,
        int minTileX,
        int maxTileX,
        int minTileY,
        int maxTileY,
        int tileSize,
        int chunkSize,
        int generationVersion) {
}
