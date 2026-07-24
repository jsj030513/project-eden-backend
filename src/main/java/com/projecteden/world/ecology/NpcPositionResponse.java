package com.projecteden.world.ecology;

/** Identity and location only; dialogue text remains a client presentation concern. */
public record NpcPositionResponse(
        Long id,
        WorldAssetType assetType,
        String displayName,
        int x,
        int y,
        TileInteractionType interactionType) { }
