package com.projecteden.world.ecology;

import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.world.chunk.WorldChunkRegionType;
import java.util.List;
import java.util.Set;

public record PhotoEcologyProfile(
        String profileKey,
        List<RecognizedObject> recognitionTypes,
        EcologyCategory ecologyCategory,
        WorldAssetType projectedAssetType,
        Set<WorldChunkRegionType> preferredRegions,
        Set<WorldChunkRegionType> allowedRegions,
        Set<TerrainType> preferredTerrain,
        Set<TerrainType> allowedTerrain,
        Set<String> spawnZoneTags,
        Set<String> avoidZoneTags,
        int maxPerChunk,
        int maxPerZone,
        int minDistanceFromPlayerSpawn,
        int minDistanceFromNpc,
        int minDistanceFromSameSpecies,
        HabitatType movementHabitat,
        EcologyFallbackPolicy fallbackPolicy,
        int priority,
        int version) {
}
