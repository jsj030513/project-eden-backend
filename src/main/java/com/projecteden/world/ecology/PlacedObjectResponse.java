package com.projecteden.world.ecology;

import com.projecteden.world.chunk.WorldChunkRegionType;

public record PlacedObjectResponse(
        Long id,
        WorldAssetType assetType,
        WorldCategory worldCategory,
        int x,
        int y,
        TerrainType terrainType,
        HabitatType habitatType,
        Long worldChangeId,
        int depth,
        int variant,
        EcologyCategory ecologyCategory,
        Long sourceRecognitionId,
        WorldChunkRegionType regionType,
        String spawnZone) {

    public PlacedObjectResponse(
            Long id, WorldAssetType assetType, WorldCategory worldCategory, int x, int y,
            TerrainType terrainType, HabitatType habitatType, Long worldChangeId, int depth, int variant) {
        this(id, assetType, worldCategory, x, y, terrainType, habitatType, worldChangeId, depth, variant,
                null, null, null, null);
    }

    public static PlacedObjectResponse from(WorldPlacedObject object) {
        WorldChange change = object.getWorldChange();
        return new PlacedObjectResponse(
                object.getId(), object.getAssetType(), change.getWorldCategory(),
                object.getPositionX(), object.getPositionY(), object.getTerrain(), object.getHabitat(),
                change.getId(), object.getPositionY(), 0, change.getEcologyCategory(),
                change.getRecognition() == null ? null : change.getRecognition().getId(),
                change.getPlacementRegionType(), change.getSpawnZoneTag());
    }
}
