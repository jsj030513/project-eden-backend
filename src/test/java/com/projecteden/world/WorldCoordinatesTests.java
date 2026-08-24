package com.projecteden.world;

import static org.assertj.core.api.Assertions.assertThat;

import com.projecteden.world.ecology.WorldCoordinates;
import org.junit.jupiter.api.Test;

class WorldCoordinatesTests {

    @Test
    void convertsTileOriginsAndPixelBoundariesUsingOneContract() {
        assertThat(WorldCoordinates.tileToPixel(0)).isZero();
        assertThat(WorldCoordinates.tileToPixel(1)).isEqualTo(48);
        assertThat(WorldCoordinates.pixelToTile(0)).isZero();
        assertThat(WorldCoordinates.pixelToTile(47)).isZero();
        assertThat(WorldCoordinates.pixelToTile(48)).isEqualTo(1);
        assertThat(WorldCoordinates.minPixelForTile(2)).isEqualTo(96);
        assertThat(WorldCoordinates.maxPixelForTile(2)).isEqualTo(143);
    }

    @Test
    void floorsNonMultipleAndNegativePixelsForFutureGlobalCoordinates() {
        assertThat(WorldCoordinates.pixelToTile(49)).isEqualTo(1);
        assertThat(WorldCoordinates.pixelToTile(-1)).isEqualTo(-1);
        assertThat(WorldCoordinates.pixelToTile(-48)).isEqualTo(-1);
        assertThat(WorldCoordinates.pixelToTile(-49)).isEqualTo(-2);
        assertThat(WorldCoordinates.tileToPixel(-2)).isEqualTo(-96);
    }

    @Test
    void mapsGlobalTilesToEightTileChunksAndLocalCoordinates() {
        assertThat(WorldCoordinates.tileToChunk(0)).isZero();
        assertThat(WorldCoordinates.tileToLocal(0)).isZero();
        assertThat(WorldCoordinates.tileToChunk(7)).isZero();
        assertThat(WorldCoordinates.tileToLocal(7)).isEqualTo(7);
        assertThat(WorldCoordinates.tileToChunk(8)).isEqualTo(1);
        assertThat(WorldCoordinates.tileToLocal(8)).isZero();
        assertThat(WorldCoordinates.pixelToChunk(8 * 48)).isEqualTo(1);
        assertThat(WorldCoordinates.chunkMinTile(2)).isEqualTo(16);
        assertThat(WorldCoordinates.chunkMaxTile(2)).isEqualTo(23);
    }

    @Test
    void keepsChunkMathReadyForFutureNegativeGlobalCoordinates() {
        assertThat(WorldCoordinates.tileToChunk(-1)).isEqualTo(-1);
        assertThat(WorldCoordinates.tileToLocal(-1)).isEqualTo(7);
        assertThat(WorldCoordinates.tileToChunk(-8)).isEqualTo(-1);
        assertThat(WorldCoordinates.tileToLocal(-8)).isZero();
        assertThat(WorldCoordinates.tileToChunk(-9)).isEqualTo(-2);
        assertThat(WorldCoordinates.tileToLocal(-9)).isEqualTo(7);
    }
}
