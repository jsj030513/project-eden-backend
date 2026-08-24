package com.projecteden.world.chunk;

import com.projecteden.world.ecology.NpcPositionResponse;
import com.projecteden.world.ecology.PlacedObjectResponse;
import com.projecteden.world.ecology.TerrainTileResponse;
import java.time.LocalDateTime;
import java.util.List;

public record WorldChunkResponse(
        int chunkX,
        int chunkY,
        WorldChunkRegionType regionType,
        String templateKey,
        int generationVersion,
        WorldChunkStatus status,
        LocalDateTime discoveredAt,
        String version,
        long npcStateVersion,
        List<TerrainTileResponse> terrain,
        List<ChunkDecorationResponse> decorations,
        List<PlacedObjectResponse> placedObjects,
        List<NpcPositionResponse> npcs,
        List<PlacedObjectResponse> animals) {
}
