package com.projecteden.world.ecology;

public enum TerrainType {
    GRASS, ROAD, SOIL, FLOWER_FIELD, FOREST, WATER, BRIDGE, BEACH, BUILDING, ROCK, CLIFF;

    public boolean isLandWalkable() {
        return this == GRASS || this == ROAD || this == SOIL || this == FLOWER_FIELD || this == FOREST
                || this == BRIDGE || this == BEACH;
    }
}
