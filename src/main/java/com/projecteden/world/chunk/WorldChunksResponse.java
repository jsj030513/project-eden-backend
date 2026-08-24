package com.projecteden.world.chunk;

import com.projecteden.world.ecology.PlayerPositionResponse;
import com.projecteden.world.ecology.TileInteractionResponse;
import java.util.List;

public record WorldChunksResponse(
        ChunkWorldResponse world,
        PlayerPositionResponse player,
        List<WorldChunkResponse> chunks,
        List<TileInteractionResponse> availableInteractions) {
}
