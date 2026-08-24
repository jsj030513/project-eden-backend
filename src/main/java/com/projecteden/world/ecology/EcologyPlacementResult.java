package com.projecteden.world.ecology;

import com.projecteden.world.chunk.WorldChunkRegionType;

public record EcologyPlacementResult(
        boolean applied,
        EcologyCategory category,
        WorldAssetType assetType,
        Long objectId,
        Integer chunkX,
        Integer chunkY,
        WorldChunkRegionType regionType,
        String spawnZone,
        EcologyPlacementReason reason,
        String profileKey,
        Integer version) {
}
