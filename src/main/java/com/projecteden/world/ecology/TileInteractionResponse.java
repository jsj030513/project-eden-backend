package com.projecteden.world.ecology;

/** A server-authoritative action available on an adjacent tile. */
public record TileInteractionResponse(
        int x,
        int y,
        TileInteractionType type,
        boolean available,
        String reason,
        Long targetId,
        WorldAssetType targetAssetType,
        String displayName,
        TileInteractionCategory category,
        String actionLabel) {

    /** Compatibility constructor for ordinary inspect interactions. */
    public TileInteractionResponse(int x, int y, TileInteractionType type, boolean available, String reason) {
        this(x, y, type, available, reason, null, null, null, null, null);
    }

    /** Compatibility constructor for the existing NPC TALK contract. */
    public TileInteractionResponse(
            int x,
            int y,
            TileInteractionType type,
            boolean available,
            String reason,
            Long targetId,
            WorldAssetType targetAssetType,
            String displayName) {
        this(x, y, type, available, reason, targetId, targetAssetType, displayName, null, null);
    }
}
