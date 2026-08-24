package com.projecteden.world.chunk;

import com.projecteden.world.ecology.HabitatType;
import com.projecteden.world.ecology.NpcPositionResponse;
import com.projecteden.world.ecology.PlacedObjectResponse;
import com.projecteden.world.ecology.PlayerPositionResponse;
import com.projecteden.world.ecology.TerrainTileResponse;
import com.projecteden.world.ecology.WorldAssetType;
import com.projecteden.world.ecology.WorldCoordinates;
import com.projecteden.world.ecology.WorldEcologyService;
import com.projecteden.world.ecology.WorldPlacedObject;
import com.projecteden.world.ecology.WorldSpatialQueryService;
import com.projecteden.world.ecology.WorldTerrainTile;
import com.projecteden.world.generation.ChunkGenerationService;
import com.projecteden.world.generation.RegionTemplateRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class WorldChunkQueryService {

    public static final int DEFAULT_RADIUS = 1;
    public static final int MAX_RADIUS = 2;

    private final WorldChunkRepository chunks;
    private final WorldSpatialQueryService spatial;
    private final WorldEcologyService ecology;
    private final ChunkGenerationService generation;
    private final RegionTemplateRegistry templates;

    public WorldChunkQueryService(
            WorldChunkRepository chunks,
            WorldSpatialQueryService spatial,
            WorldEcologyService ecology,
            ChunkGenerationService generation,
            RegionTemplateRegistry templates) {
        this.chunks = chunks;
        this.spatial = spatial;
        this.ecology = ecology;
        this.generation = generation;
        this.templates = templates;
    }

    public WorldChunksResponse chunksForUser(Long userId, int centerChunkX, int centerChunkY, int radius) {
        if (radius < 0 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("INVALID_CHUNK_RADIUS");
        }
        var context = ecology.chunkReadContextForUser(userId);
        var world = context.world();

        int worldMinChunkX = WorldCoordinates.tileToChunk(world.getMinTileX());
        int worldMaxChunkX = WorldCoordinates.tileToChunk(world.getMaxTileX());
        int worldMinChunkY = WorldCoordinates.tileToChunk(world.getMinTileY());
        int worldMaxChunkY = WorldCoordinates.tileToChunk(world.getMaxTileY());
        int minChunkX = Math.max(worldMinChunkX, centerChunkX - radius);
        int maxChunkX = Math.min(worldMaxChunkX, centerChunkX + radius);
        int minChunkY = Math.max(worldMinChunkY, centerChunkY - radius);
        int maxChunkY = Math.min(worldMaxChunkY, centerChunkY + radius);

        List<WorldChunk> requested = minChunkX > maxChunkX || minChunkY > maxChunkY
                ? List.of()
                : generation.ensureRange(
                        world.getId(), minChunkX, maxChunkX, minChunkY, maxChunkY);
        if (requested.isEmpty()) {
            return response(world, context.playerPosition(), context.availableInteractions(), List.of());
        }

        int minTileX = Math.max(world.getMinTileX(), WorldCoordinates.chunkMinTile(minChunkX));
        int maxTileX = Math.min(world.getMaxTileX(), WorldCoordinates.chunkMaxTile(maxChunkX));
        int minTileY = Math.max(world.getMinTileY(), WorldCoordinates.chunkMinTile(minChunkY));
        int maxTileY = Math.min(world.getMaxTileY(), WorldCoordinates.chunkMaxTile(maxChunkY));
        List<WorldTerrainTile> terrain = spatial.terrainInTileRange(
                context.characterId(), minTileX, maxTileX, minTileY, maxTileY);
        List<WorldPlacedObject> objects = spatial.objectsInTileRange(
                context.characterId(), minTileX, maxTileX, minTileY, maxTileY);
        Set<Long> replacedObjectIds = objects.stream()
                .map(WorldPlacedObject::getWorldChange)
                .map(change -> change.getTargetObject())
                .filter(java.util.Objects::nonNull)
                .map(WorldPlacedObject::getId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        Map<String, List<WorldTerrainTile>> terrainByChunk = terrain.stream()
                .collect(java.util.stream.Collectors.groupingBy(tile ->
                        key(WorldCoordinates.tileToChunk(tile.getX()), WorldCoordinates.tileToChunk(tile.getY()))));
        Map<String, List<WorldPlacedObject>> objectsByChunk = objects.stream()
                .filter(object -> !replacedObjectIds.contains(object.getId()))
                .filter(object -> !isNpc(object.getAssetType()))
                .collect(java.util.stream.Collectors.groupingBy(object -> key(
                        WorldCoordinates.pixelToChunk(object.getPositionX()),
                        WorldCoordinates.pixelToChunk(object.getPositionY()))));
        Map<String, List<NpcPositionResponse>> npcsByChunk = context.npcPositions().stream()
                .collect(java.util.stream.Collectors.groupingBy(npc -> key(
                        WorldCoordinates.tileToChunk(npc.x()),
                        WorldCoordinates.tileToChunk(npc.y()))));

        List<WorldChunkResponse> payload = requested.stream().map(chunk -> {
            String key = key(chunk.getChunkX(), chunk.getChunkY());
            List<WorldTerrainTile> chunkTerrain = terrainByChunk.getOrDefault(key, List.of()).stream()
                    .sorted(Comparator.comparingInt(WorldTerrainTile::getY).thenComparingInt(WorldTerrainTile::getX))
                    .toList();
            List<WorldPlacedObject> chunkObjects = objectsByChunk.getOrDefault(key, List.of()).stream()
                    .sorted(Comparator.comparing(WorldPlacedObject::getId))
                    .toList();
            List<PlacedObjectResponse> placed = chunkObjects.stream().map(this::placed).toList();
            List<ChunkDecorationResponse> decorations = chunk.getRegionType()
                    == com.projecteden.world.chunk.WorldChunkRegionType.HUB
                    ? List.of()
                    : templates.requireByKey(chunk.getTemplateKey()).optionalDecorations().stream()
                            .map(decoration -> new ChunkDecorationResponse(
                                    decoration.type(), decoration.x(), decoration.y()))
                            .toList();
            List<NpcPositionResponse> npcs = npcsByChunk.getOrDefault(key, List.of()).stream()
                    .sorted(Comparator.comparing(NpcPositionResponse::objectId))
                    .toList();
            List<PlacedObjectResponse> animals = placed.stream()
                    .filter(object -> object.habitatType() == HabitatType.LAND
                            || object.habitatType() == HabitatType.WATER
                            || object.habitatType() == HabitatType.AMPHIBIOUS
                            || object.habitatType() == HabitatType.AIR)
                    .filter(object -> !isNpc(object.assetType()))
                    .toList();
            return new WorldChunkResponse(
                    chunk.getChunkX(), chunk.getChunkY(), chunk.getRegionType(), chunk.getTemplateKey(),
                    chunk.getGenerationVersion(), chunk.getStatus(), chunk.getDiscoveredAt(),
                    contentVersion(chunk, chunkTerrain, chunkObjects),
                    npcs.stream().mapToLong(NpcPositionResponse::stateVersion).max().orElse(0),
                    chunkTerrain.stream().map(tile -> new TerrainTileResponse(
                            tile.getX(), tile.getY(), tile.getTerrainType(), tile.isWalkable())).toList(),
                    decorations, placed, npcs, animals);
        }).toList();
        return response(world, context.playerPosition(), context.availableInteractions(), payload);
    }

    private WorldChunksResponse response(
            com.projecteden.world.domain.World world,
            PlayerPositionResponse player,
            List<com.projecteden.world.ecology.TileInteractionResponse> interactions,
            List<WorldChunkResponse> payload) {
        return new WorldChunksResponse(
                new ChunkWorldResponse(
                        world.getId(), world.getMinTileX(), world.getMaxTileX(),
                        world.getMinTileY(), world.getMaxTileY(), WorldCoordinates.TILE_SIZE,
                        WorldCoordinates.CHUNK_SIZE, world.getWorldGenerationVersion()),
                player, payload, interactions);
    }

    private PlacedObjectResponse placed(WorldPlacedObject object) {
        return PlacedObjectResponse.from(object);
    }

    private static String contentVersion(
            WorldChunk chunk,
            List<WorldTerrainTile> terrain,
            List<WorldPlacedObject> objects) {
        StringBuilder content = new StringBuilder()
                .append(chunk.getGenerationVersion()).append('|')
                .append(chunk.getRegionType()).append('|')
                .append(chunk.getTemplateKey()).append('|')
                .append(chunk.getStatus()).append('|')
                .append(chunk.getDiscoveredAt()).append('|');
        terrain.forEach(tile -> content.append(tile.getX()).append(',')
                .append(tile.getY()).append(',').append(tile.getTerrainType()).append(',')
                .append(tile.isWalkable()).append(';'));
        objects.forEach(object -> content.append(object.getId()).append(',')
                .append(object.getAssetType()).append(',').append(object.getPositionX()).append(',')
                .append(object.getPositionY()).append(',').append(object.getTerrain()).append(';'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static boolean isNpc(WorldAssetType type) {
        return type == WorldAssetType.DEFAULT_NPC_GUIDE
                || type == WorldAssetType.DEFAULT_NPC_GARDENER
                || type == WorldAssetType.DEFAULT_NPC_MEMORY_KEEPER
                || type == WorldAssetType.DEFAULT_NPC_ANIMAL_CARETAKER;
    }

    private static String npcName(WorldAssetType type) {
        return switch (type) {
            case DEFAULT_NPC_GUIDE -> "마을 안내자";
            case DEFAULT_NPC_GARDENER -> "정원 관리인";
            case DEFAULT_NPC_MEMORY_KEEPER -> "기억 보관인";
            case DEFAULT_NPC_ANIMAL_CARETAKER -> "동물 돌봄이";
            default -> "마을 주민";
        };
    }

    private static String key(int chunkX, int chunkY) {
        return chunkX + ":" + chunkY;
    }
}
