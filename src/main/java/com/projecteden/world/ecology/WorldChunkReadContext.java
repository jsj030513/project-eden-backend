package com.projecteden.world.ecology;

import com.projecteden.world.domain.World;
import java.util.List;

public record WorldChunkReadContext(
        World world,
        Long characterId,
        PlayerPositionResponse playerPosition,
        List<TileInteractionResponse> availableInteractions,
        List<NpcPositionResponse> npcPositions) {
}
