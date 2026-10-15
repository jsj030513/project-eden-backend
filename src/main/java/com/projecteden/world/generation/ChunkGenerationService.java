package com.projecteden.world.generation;

import com.projecteden.world.chunk.WorldChunk;
import com.projecteden.world.chunk.WorldChunkManager;
import com.projecteden.world.chunk.WorldChunkRepository;
import com.projecteden.world.chunk.WorldChunkStatus;
import com.projecteden.world.domain.World;
import com.projecteden.world.ecology.WorldCoordinates;
import com.projecteden.world.ecology.WorldTerrainTile;
import com.projecteden.world.ecology.WorldTerrainTileRepository;
import com.projecteden.world.ecology.WorldTerrainBatchWriter;
import com.projecteden.world.ecology.WorldTerrainBatchWriter.TerrainSeed;
import com.projecteden.world.repository.WorldRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChunkGenerationService {

    private static final int EXPECTED_TERRAIN_ROWS = 64;
    private final WorldRepository worlds;
    private final WorldChunkRepository chunks;
    private final WorldTerrainTileRepository terrain;
    private final WorldTerrainBatchWriter terrainWriter;
    private final RegionSelectionPolicy selection;
    private final RegionTemplateRegistry templates;

    public ChunkGenerationService(
            WorldRepository worlds,
            WorldChunkRepository chunks,
            WorldTerrainTileRepository terrain,
            WorldTerrainBatchWriter terrainWriter,
            RegionSelectionPolicy selection,
            RegionTemplateRegistry templates) {
        this.worlds = worlds;
        this.chunks = chunks;
        this.terrain = terrain;
        this.terrainWriter = terrainWriter;
        this.selection = selection;
        this.templates = templates;
    }

    @Transactional
    public WorldChunk ensureGenerated(Long worldId, int chunkX, int chunkY) {
        if (WorldChunkManager.isHubChunk(chunkX, chunkY)) {
            WorldChunk existingHub = chunks.findByWorldIdAndChunkXAndChunkY(worldId, chunkX, chunkY)
                    .orElse(null);
            if (existingHub != null) return existingHub;
        }
        World world = worlds.findByIdForUpdate(worldId)
                .orElseThrow(() -> new IllegalArgumentException("WORLD_NOT_FOUND"));
        validateBounds(world, chunkX, chunkY);
        if (WorldChunkManager.isHubChunk(chunkX, chunkY)) {
            return chunks.findForUpdate(worldId, chunkX, chunkY)
                    .orElseThrow(() -> new IllegalStateException("HUB_CHUNK_MISSING"));
        }

        int minX = WorldCoordinates.chunkMinTile(chunkX);
        int maxX = WorldCoordinates.chunkMaxTile(chunkX);
        int minY = WorldCoordinates.chunkMinTile(chunkY);
        int maxY = WorldCoordinates.chunkMaxTile(chunkY);
        WorldChunk existing = chunks.findForUpdate(worldId, chunkX, chunkY).orElse(null);
        long rowCount = terrain.countByCharacterIdAndXBetweenAndYBetween(
                world.getCharacter().getId(), minX, maxX, minY, maxY);
        if (existing != null && existing.getStatus() == WorldChunkStatus.GENERATED
                && existing.getGenerationVersion() == RegionTemplateRegistry.GENERATION_VERSION
                && rowCount == EXPECTED_TERRAIN_ROWS) {
            return existing;
        }

        var regionType = selection.select(world, chunkX, chunkY);
        RegionTemplate template = templates.require(regionType);
        applyTerrain(world, chunkX, chunkY, template);
        if (existing == null) {
            existing = chunks.save(WorldChunk.generated(
                    world, chunkX, chunkY, regionType,
                    template.templateKey(), RegionTemplateRegistry.GENERATION_VERSION));
        } else {
            existing.repair(
                    regionType, template.templateKey(),
                    RegionTemplateRegistry.GENERATION_VERSION);
        }
        return existing;
    }

    @Transactional
    public List<WorldChunk> ensureRange(
            Long worldId,
            int minChunkX,
            int maxChunkX,
            int minChunkY,
            int maxChunkY) {
        for (int chunkY = minChunkY; chunkY <= maxChunkY; chunkY++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                ensureGenerated(worldId, chunkX, chunkY);
            }
        }
        return chunks.findByWorldIdAndChunkXBetweenAndChunkYBetweenOrderByChunkYAscChunkXAsc(
                worldId, minChunkX, maxChunkX, minChunkY, maxChunkY);
    }

    private void applyTerrain(
            World world,
            int chunkX,
            int chunkY,
            RegionTemplate template) {
        int minX = WorldCoordinates.chunkMinTile(chunkX);
        int maxX = WorldCoordinates.chunkMaxTile(chunkX);
        int minY = WorldCoordinates.chunkMinTile(chunkY);
        int maxY = WorldCoordinates.chunkMaxTile(chunkY);
        Map<String, WorldTerrainTile> existing = new HashMap<>();
        terrain.findByCharacterIdAndXBetweenAndYBetweenOrderByYAscXAsc(
                        world.getCharacter().getId(), minX, maxX, minY, maxY)
                .forEach(tile -> existing.put(tile.getX() + ":" + tile.getY(), tile));

        List<TerrainSeed> missingTiles = new ArrayList<>(EXPECTED_TERRAIN_ROWS);
        for (int localY = 0; localY < 8; localY++) {
            for (int localX = 0; localX < 8; localX++) {
                int x = minX + localX;
                int y = minY + localY;
                var expected = RegionTemplateValidator.terrain(
                        template.terrainPattern().get(localY).charAt(localX));
                WorldTerrainTile persisted = existing.get(x + ":" + y);
                if (persisted == null) {
                    missingTiles.add(new TerrainSeed(x, y, expected));
                } else if (persisted.getTerrainType() != expected) {
                    persisted.changeTerrain(expected);
                }
            }
        }
        terrainWriter.insertMissing(world.getCharacter().getId(), missingTiles);
    }

    private static void validateBounds(World world, int chunkX, int chunkY) {
        int minX = WorldCoordinates.chunkMinTile(chunkX);
        int maxX = WorldCoordinates.chunkMaxTile(chunkX);
        int minY = WorldCoordinates.chunkMinTile(chunkY);
        int maxY = WorldCoordinates.chunkMaxTile(chunkY);
        if (minX < world.getMinTileX() || maxX > world.getMaxTileX()
                || minY < world.getMinTileY() || maxY > world.getMaxTileY()) {
            throw new IllegalArgumentException("CHUNK_OUT_OF_BOUNDS");
        }
    }
}
