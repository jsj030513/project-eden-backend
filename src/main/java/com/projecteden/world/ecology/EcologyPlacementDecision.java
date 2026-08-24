package com.projecteden.world.ecology;

import com.projecteden.world.chunk.WorldChunkRegionType;

record EcologyPlacementDecision(
        PhotoEcologyProfile profile,
        boolean applied,
        int tileX,
        int tileY,
        Integer chunkX,
        Integer chunkY,
        WorldChunkRegionType regionType,
        String zoneTag,
        EcologyPlacementReason reason) {

    static EcologyPlacementDecision failed(PhotoEcologyProfile profile, EcologyPlacementReason reason) {
        return new EcologyPlacementDecision(profile, false, 0, 0, null, null, null, null, reason);
    }
}
