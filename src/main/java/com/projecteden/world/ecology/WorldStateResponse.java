package com.projecteden.world.ecology;

import java.util.List;

public record WorldStateResponse(
        Long worldId,
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
        String villageTitle,
        int tileSize,
        int generationVersion) {

    public WorldStateResponse(
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
            String villageTitle) {
        this(changes, terrainTiles, placedObjects, mapBounds, playerPosition, availableInteractions,
                npcPositions, growth, themeScores, dominantTheme, villageTitle,
                WorldCoordinates.TILE_SIZE, 1);
    }

    public WorldStateResponse(
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
            String villageTitle,
            int tileSize,
            int generationVersion) {
        this(null, changes, terrainTiles, placedObjects, mapBounds, playerPosition, availableInteractions,
                npcPositions, growth, themeScores, dominantTheme, villageTitle,
                tileSize, generationVersion);
    }
}
