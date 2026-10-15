package com.projecteden.world.ecology;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Range-query seam for Phase 3-B. Public state remains full-state in Phase 3-A.
 */
@Service
public class WorldSpatialQueryService {

    private final WorldTerrainTileRepository terrain;
    private final WorldPlacedObjectRepository objects;

    public WorldSpatialQueryService(
            WorldTerrainTileRepository terrain,
            WorldPlacedObjectRepository objects) {
        this.terrain = terrain;
        this.objects = objects;
    }

    @Transactional(readOnly = true)
    public List<WorldTerrainTile> terrainInTileRange(
            Long characterId,
            int minTileX,
            int maxTileX,
            int minTileY,
            int maxTileY) {
        validateRange(minTileX, maxTileX, minTileY, maxTileY);
        return terrain.findByCharacterIdAndXBetweenAndYBetweenOrderByYAscXAsc(
                characterId, minTileX, maxTileX, minTileY, maxTileY);
    }

    @Transactional(readOnly = true)
    public List<WorldPlacedObject> objectsInTileRange(
            Long characterId,
            int minTileX,
            int maxTileX,
            int minTileY,
            int maxTileY) {
        validateRange(minTileX, maxTileX, minTileY, maxTileY);
        return objects.findByCharacterIdAndPixelRangeOrderByIdAsc(
                characterId,
                WorldCoordinates.minPixelForTile(minTileX),
                WorldCoordinates.maxPixelForTile(maxTileX),
                WorldCoordinates.minPixelForTile(minTileY),
                WorldCoordinates.maxPixelForTile(maxTileY));
    }

    private static void validateRange(int minX, int maxX, int minY, int maxY) {
        if (minX > maxX || minY > maxY) {
            throw new IllegalArgumentException("INVALID_WORLD_TILE_RANGE");
        }
    }
}
