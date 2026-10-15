package com.projecteden.world.ecology;

/**
 * The persisted terrain/player contract uses global tile coordinates while
 * placed objects retain the existing global pixel-coordinate contract.
 */
public final class WorldCoordinates {

    public static final int TILE_SIZE = 48;
    public static final int CHUNK_SIZE = 8;

    private WorldCoordinates() {
    }

    public static int tileToPixel(int tileCoordinate) {
        return Math.multiplyExact(tileCoordinate, TILE_SIZE);
    }

    /**
     * Object anchors that are not exact tile multiples belong to the tile
     * containing the pixel. Math.floorDiv keeps the same rule for future
     * negative global coordinates.
     */
    public static int pixelToTile(int pixelCoordinate) {
        return Math.floorDiv(pixelCoordinate, TILE_SIZE);
    }

    public static int minPixelForTile(int tileCoordinate) {
        return tileToPixel(tileCoordinate);
    }

    public static int maxPixelForTile(int tileCoordinate) {
        return Math.addExact(tileToPixel(tileCoordinate), TILE_SIZE - 1);
    }

    public static int tileToChunk(int tileCoordinate) {
        return Math.floorDiv(tileCoordinate, CHUNK_SIZE);
    }

    public static int tileToLocal(int tileCoordinate) {
        return Math.floorMod(tileCoordinate, CHUNK_SIZE);
    }

    public static int pixelToChunk(int pixelCoordinate) {
        return tileToChunk(pixelToTile(pixelCoordinate));
    }

    public static int chunkMinTile(int chunkCoordinate) {
        return Math.multiplyExact(chunkCoordinate, CHUNK_SIZE);
    }

    public static int chunkMaxTile(int chunkCoordinate) {
        return Math.addExact(chunkMinTile(chunkCoordinate), CHUNK_SIZE - 1);
    }
}
