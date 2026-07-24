package com.projecteden.world.ecology;

import java.util.List;

public record WorldStateResponse(
        List<WorldChangeResult> changes,
        List<TerrainTileResponse> terrainTiles,
        List<PlacedObjectResponse> placedObjects,
        MapBoundsResponse mapBounds,
        PlayerPositionResponse playerPosition,
        List<TileInteractionResponse> availableInteractions,
        List<NpcPositionResponse> npcPositions,
        List<String> growth,
        List<String> themeScores,
        String dominantTheme,
        String villageTitle) { }
