package com.projecteden.world.generation;

import com.projecteden.world.chunk.WorldChunkRegionType;
import com.projecteden.world.ecology.EcologyCategory;
import com.projecteden.world.ecology.TerrainType;
import com.projecteden.world.ecology.WorldAssetType;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record RegionTemplate(
        String templateKey,
        WorldChunkRegionType regionType,
        int width,
        int height,
        List<String> terrainPattern,
        List<String> requiredObjects,
        List<Decoration> optionalDecorations,
        Map<String, Connector> connectors,
        List<Zone> spawnZones,
        List<Zone> interactionZones) {

    public record Decoration(String type, int x, int y) {
    }

    public record Connector(ChunkConnectorType type, int offset) {
    }

    public record Zone(
            String tag,
            int x,
            int y,
            int width,
            int height,
            Set<EcologyCategory> allowedEcologyCategories,
            Set<WorldAssetType> allowedAssetTypes,
            Set<TerrainType> terrainRequirements,
            int capacity,
            int minSpacing,
            boolean walkableRequired,
            boolean interactionAccessRequired,
            boolean movementAllowed,
            int priority) {
    }
}
