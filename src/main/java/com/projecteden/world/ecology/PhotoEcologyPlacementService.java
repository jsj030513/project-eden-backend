package com.projecteden.world.ecology;

import com.projecteden.ai.domain.Recognition;
import com.projecteden.world.chunk.WorldChunk;
import com.projecteden.world.chunk.WorldChunkRegionType;
import com.projecteden.world.chunk.WorldChunkRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.generation.RegionTemplate;
import com.projecteden.world.generation.RegionTemplateRegistry;
import com.projecteden.world.npc.NpcRuntimeStateRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PhotoEcologyPlacementService {
    private static final int MAX_ECOLOGY_OBJECTS_PER_CHUNK = 12;
    private final PhotoEcologyProfileRegistry profiles;
    private final WorldChunkRepository chunks;
    private final RegionTemplateRegistry regionTemplates;
    private final WorldTerrainTileRepository terrain;
    private final WorldPlacedObjectRepository objects;
    private final WorldPlayerPositionRepository playerPositions;
    private final NpcRuntimeStateRepository npcStates;

    public PhotoEcologyPlacementService(
            PhotoEcologyProfileRegistry profiles,
            WorldChunkRepository chunks,
            RegionTemplateRegistry regionTemplates,
            WorldTerrainTileRepository terrain,
            WorldPlacedObjectRepository objects,
            WorldPlayerPositionRepository playerPositions,
            NpcRuntimeStateRepository npcStates) {
        this.profiles = profiles;
        this.chunks = chunks;
        this.regionTemplates = regionTemplates;
        this.terrain = terrain;
        this.objects = objects;
        this.playerPositions = playerPositions;
        this.npcStates = npcStates;
    }

    EcologyPlacementDecision select(Recognition recognition, World world) {
        PhotoEcologyProfile profile = profiles.require(recognition.getRecognizedObject());
        if (profile.ecologyCategory() == EcologyCategory.NON_PLACEABLE) {
            return EcologyPlacementDecision.failed(profile, EcologyPlacementReason.PROFILE_NOT_PLACEABLE);
        }
        Long characterId = recognition.getPhoto().getCharacter().getId();
        List<WorldChunk> compatibleChunks = chunks.findGeneratedDiscovered(world.getId()).stream()
                .filter(chunk -> profile.allowedRegions().contains(chunk.getRegionType()))
                .toList();
        if (compatibleChunks.isEmpty()) {
            return EcologyPlacementDecision.failed(profile, EcologyPlacementReason.NO_COMPATIBLE_REGION);
        }

        List<ZoneCandidate> zoneCandidates = compatibleChunks.stream()
                .flatMap(chunk -> zones(chunk).stream()
                        .filter(zone -> zone.allowedCategories().contains(profile.ecologyCategory()))
                        .filter(zone -> zone.allowedAssets().isEmpty() || zone.allowedAssets().contains(profile.projectedAssetType()))
                        .filter(zone -> profile.spawnZoneTags().contains(zone.tag()))
                        .filter(zone -> !profile.avoidZoneTags().contains(zone.tag()))
                        .flatMap(zone -> coordinates(chunk, zone).stream()))
                .filter(candidate -> world.containsTile(candidate.tileX(), candidate.tileY()))
                .toList();
        if (zoneCandidates.isEmpty()) {
            return EcologyPlacementDecision.failed(profile, EcologyPlacementReason.NO_COMPATIBLE_REGION);
        }

        Map<String, ZoneCandidate> candidatesByKey = zoneCandidates.stream().collect(Collectors.toMap(
                candidate -> tileKey(candidate.tileX(), candidate.tileY()), candidate -> candidate,
                (left, right) -> left.zone().priority() >= right.zone().priority() ? left : right));
        Set<Integer> candidateKeys = new HashSet<>();
        candidatesByKey.values().forEach(candidate -> {
            candidateKeys.add(coordinateKey(candidate.tileX(), candidate.tileY()));
            for (int[] direction : DIRECTIONS) candidateKeys.add(coordinateKey(
                    candidate.tileX() + direction[0], candidate.tileY() + direction[1]));
        });
        Map<String, WorldTerrainTile> tiles = terrain.findCandidateTiles(characterId, candidateKeys).stream()
                .collect(Collectors.toMap(tile -> tileKey(tile.getX(), tile.getY()), tile -> tile));
        List<WorldPlacedObject> existing = objects.findByCharacterIdOrderByIdAsc(characterId);
        List<WorldPlacedObject> ecologyExisting = existing.stream()
                .filter(object -> object.getWorldChange().getRecognition() != null)
                .filter(object -> object.getWorldChange().getPlacementVersion() != null
                        && object.getWorldChange().getPlacementVersion() >= 1)
                .filter(object -> object.getWorldChange().getTargetObject() == null)
                .toList();
        Set<String> occupied = existing.stream().map(object -> tileKey(
                WorldCoordinates.pixelToTile(object.getPositionX()),
                WorldCoordinates.pixelToTile(object.getPositionY()))).collect(Collectors.toSet());
        Set<String> npcTiles = npcStates.findByWorldIdOrderByNpcObjectIdAsc(world.getId()).stream()
                .map(state -> tileKey(state.getTileX(), state.getTileY())).collect(Collectors.toSet());
        int[] player = playerPositions.findByCharacterId(characterId)
                .map(position -> new int[]{position.getX(), position.getY()})
                .orElse(new int[]{11, 8});

        Map<String, Long> chunkCounts = ecologyExisting.stream().collect(Collectors.groupingBy(
                object -> WorldCoordinates.pixelToChunk(object.getPositionX()) + ":" + WorldCoordinates.pixelToChunk(object.getPositionY()),
                Collectors.counting()));
        Map<String, Long> zoneCounts = countByZone(ecologyExisting, zoneCandidates);
        boolean capacityRejected = false;
        List<ScoredCandidate> safe = new ArrayList<>();
        for (ZoneCandidate candidate : candidatesByKey.values()) {
            String chunkKey = candidate.chunk().getChunkX() + ":" + candidate.chunk().getChunkY();
            String zoneKey = zoneKey(candidate);
            if (chunkCounts.getOrDefault(chunkKey, 0L) >= Math.min(profile.maxPerChunk(), MAX_ECOLOGY_OBJECTS_PER_CHUNK)
                    || zoneCounts.getOrDefault(zoneKey, 0L) >= Math.min(profile.maxPerZone(), candidate.zone().capacity())) {
                capacityRejected = true;
                continue;
            }
            WorldTerrainTile tile = tiles.get(tileKey(candidate.tileX(), candidate.tileY()));
            if (!safeTile(profile, candidate, tile, tiles, occupied, npcTiles, player, existing)) continue;
            safe.add(new ScoredCandidate(candidate, score(world, recognition, profile, candidate, tile, player)));
        }
        if (safe.isEmpty()) return EcologyPlacementDecision.failed(profile,
                capacityRejected ? EcologyPlacementReason.CAPACITY_REACHED : EcologyPlacementReason.NO_SAFE_SPAWN_TILE);
        ZoneCandidate chosen = safe.stream()
                .sorted(Comparator.comparingLong(ScoredCandidate::score).reversed()
                        .thenComparing(entry -> entry.candidate().chunk().getChunkY())
                        .thenComparing(entry -> entry.candidate().chunk().getChunkX())
                        .thenComparing(entry -> entry.candidate().zone().tag())
                        .thenComparing(entry -> entry.candidate().tileY())
                        .thenComparing(entry -> entry.candidate().tileX()))
                .findFirst().orElseThrow().candidate();
        return new EcologyPlacementDecision(profile, true, chosen.tileX(), chosen.tileY(),
                chosen.chunk().getChunkX(), chosen.chunk().getChunkY(), chosen.chunk().getRegionType(),
                chosen.zone().tag(), EcologyPlacementReason.PLACED);
    }

    EcologyPlacementDecision unavailable(Recognition recognition) {
        PhotoEcologyProfile profile = profiles.require(recognition.getRecognizedObject());
        return EcologyPlacementDecision.failed(profile,
                profile.ecologyCategory() == EcologyCategory.NON_PLACEABLE
                        ? EcologyPlacementReason.PROFILE_NOT_PLACEABLE
                        : EcologyPlacementReason.NO_COMPATIBLE_REGION);
    }

    public boolean movementAllowed(WorldPlacedObject animal, World world, int tileX, int tileY) {
        WorldChange change = animal.getWorldChange();
        if (change.getRecognition() == null || change.getEcologyProfileKey() == null
                || "LEGACY".equals(change.getEcologyProfileKey())) return true;
        PhotoEcologyProfile profile = profiles.require(change.getRecognition().getRecognizedObject());
        return chunks.findByWorldIdAndChunkXAndChunkY(
                        world.getId(), WorldCoordinates.tileToChunk(tileX), WorldCoordinates.tileToChunk(tileY))
                .filter(chunk -> chunk.getDiscoveredAt() != null)
                .filter(chunk -> profile.allowedRegions().contains(chunk.getRegionType()))
                .isPresent();
    }

    private boolean safeTile(
            PhotoEcologyProfile profile,
            ZoneCandidate candidate,
            WorldTerrainTile tile,
            Map<String, WorldTerrainTile> tiles,
            Set<String> occupied,
            Set<String> npcTiles,
            int[] player,
            List<WorldPlacedObject> existing) {
        if (tile == null || (candidate.zone().walkableRequired() && !tile.isWalkable())
                || !candidate.zone().terrainRequirements().contains(tile.getTerrainType())
                || !profile.allowedTerrain().contains(tile.getTerrainType())
                || tile.getTerrainType() == TerrainType.WATER || tile.getTerrainType() == TerrainType.BUILDING
                || tile.getTerrainType() == TerrainType.SOIL || tile.getTerrainType() == TerrainType.ROAD
                || tile.getTerrainType() == TerrainType.BRIDGE) return false;
        String key = tileKey(tile.getX(), tile.getY());
        if (occupied.contains(key) || npcTiles.contains(key)
                || distance(tile.getX(), tile.getY(), player[0], player[1]) < profile.minDistanceFromPlayerSpawn()) return false;
        if (npcTiles.stream().map(PhotoEcologyPlacementService::parseTile)
                .anyMatch(position -> distance(tile.getX(), tile.getY(), position[0], position[1]) < profile.minDistanceFromNpc())) return false;
        if (existing.stream().filter(object -> object.getAssetType() == profile.projectedAssetType())
                .anyMatch(object -> distance(tile.getX(), tile.getY(),
                        WorldCoordinates.pixelToTile(object.getPositionX()),
                        WorldCoordinates.pixelToTile(object.getPositionY())) < Math.max(profile.minDistanceFromSameSpecies(), candidate.zone().minSpacing()))) return false;
        if (!candidate.zone().interactionAccessRequired()) return true;
        long accessible = java.util.Arrays.stream(DIRECTIONS)
                .map(direction -> tiles.get(tileKey(tile.getX() + direction[0], tile.getY() + direction[1])))
                .filter(java.util.Objects::nonNull)
                .filter(WorldTerrainTile::isWalkable)
                .filter(neighbor -> neighbor.getTerrainType() != TerrainType.WATER && neighbor.getTerrainType() != TerrainType.BUILDING)
                .filter(neighbor -> !occupied.contains(tileKey(neighbor.getX(), neighbor.getY())))
                .count();
        return accessible > 0;
    }

    private static Map<String, Long> countByZone(List<WorldPlacedObject> existing, List<ZoneCandidate> candidates) {
        Map<String, Long> counts = new HashMap<>();
        for (WorldPlacedObject object : existing) {
            int tileX = WorldCoordinates.pixelToTile(object.getPositionX());
            int tileY = WorldCoordinates.pixelToTile(object.getPositionY());
            candidates.stream().filter(candidate -> candidate.tileX() == tileX && candidate.tileY() == tileY)
                    .map(PhotoEcologyPlacementService::zoneKey).distinct()
                    .forEach(key -> counts.merge(key, 1L, Long::sum));
        }
        return counts;
    }

    private List<Zone> zones(WorldChunk chunk) {
        if (chunk.getRegionType() == WorldChunkRegionType.HUB) return hubZones();
        return regionTemplates.require(chunk.getRegionType()).spawnZones().stream()
                .map(zone -> new Zone(zone.tag(), zone.x(), zone.y(), zone.width(), zone.height(),
                        zone.allowedEcologyCategories(), zone.allowedAssetTypes(), zone.terrainRequirements(),
                        zone.capacity(), zone.minSpacing(), zone.walkableRequired(), zone.interactionAccessRequired(),
                        zone.movementAllowed(), zone.priority())).toList();
    }

    private static List<Zone> hubZones() {
        Set<EcologyCategory> all = Set.of(EcologyCategory.ANIMAL, EcologyCategory.PLANT, EcologyCategory.MEMORY_OBJECT);
        Set<TerrainType> ground = Set.of(TerrainType.GRASS, TerrainType.FLOWER_FIELD);
        return List.of(
                new Zone("HOUSE_EDGE", 1, 5, 6, 2, all, Set.of(), ground, 3, 1, true, true, true, 80),
                new Zone("FARM_EDGE", 1, 8, 6, 5, all, Set.of(), ground, 4, 1, true, true, true, 85),
                new Zone("FLOWER_GARDEN", 12, 7, 5, 3, all, Set.of(), ground, 4, 1, true, true, true, 90),
                new Zone("POND_EDGE", 15, 9, 3, 3, all, Set.of(), ground, 2, 1, true, true, true, 95),
                new Zone("SAFE_MEMORY_ZONE", 12, 2, 5, 4, all, Set.of(), ground, 4, 1, true, true, true, 70));
    }

    private static List<ZoneCandidate> coordinates(WorldChunk chunk, Zone zone) {
        List<ZoneCandidate> result = new ArrayList<>();
        int originX = WorldCoordinates.chunkMinTile(chunk.getChunkX());
        int originY = WorldCoordinates.chunkMinTile(chunk.getChunkY());
        for (int localY = zone.y(); localY < zone.y() + zone.height(); localY++) {
            for (int localX = zone.x(); localX < zone.x() + zone.width(); localX++) {
                int globalX = chunk.getRegionType() == WorldChunkRegionType.HUB ? localX : originX + localX;
                int globalY = chunk.getRegionType() == WorldChunkRegionType.HUB ? localY : originY + localY;
                if (WorldCoordinates.tileToChunk(globalX) == chunk.getChunkX()
                        && WorldCoordinates.tileToChunk(globalY) == chunk.getChunkY()) {
                    result.add(new ZoneCandidate(chunk, zone, globalX, globalY));
                }
            }
        }
        return result;
    }

    private static long score(World world, Recognition recognition, PhotoEcologyProfile profile,
                              ZoneCandidate candidate, WorldTerrainTile tile, int[] player) {
        long score = profile.priority() * 10_000L + candidate.zone().priority() * 1_000L;
        if (profile.preferredRegions().contains(candidate.chunk().getRegionType())) score += 1_000_000L;
        if (profile.preferredTerrain().contains(tile.getTerrainType())) score += 100_000L;
        score += Math.min(50, distance(tile.getX(), tile.getY(), player[0], player[1])) * 100L;
        String tie = world.getSeed() + ":" + recognition.getId() + ":" + recognition.getPhoto().getId()
                + ":" + profile.projectedAssetType() + ":" + candidate.chunk().getChunkX() + ":"
                + candidate.chunk().getChunkY() + ":" + candidate.zone().tag() + ":" + tile.getX() + ":" + tile.getY();
        return score + Math.floorMod(stableHash(tie), 100L);
    }

    private static long stableHash(String value) {
        long hash = 0xcbf29ce484222325L;
        for (byte part : value.getBytes(StandardCharsets.UTF_8)) { hash ^= part & 0xff; hash *= 0x100000001b3L; }
        return hash;
    }

    private static int distance(int x1, int y1, int x2, int y2) { return Math.abs(x1 - x2) + Math.abs(y1 - y2); }
    private static int coordinateKey(int x, int y) { return x * 1000 + y; }
    private static String tileKey(int x, int y) { return x + ":" + y; }
    private static int[] parseTile(String value) { String[] parts = value.split(":"); return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])}; }
    private static String zoneKey(ZoneCandidate candidate) { return candidate.chunk().getChunkX() + ":" + candidate.chunk().getChunkY() + ":" + candidate.zone().tag(); }
    private static final int[][] DIRECTIONS = {{1,0},{-1,0},{0,1},{0,-1}};
    private record Zone(String tag, int x, int y, int width, int height,
                        Set<EcologyCategory> allowedCategories, Set<WorldAssetType> allowedAssets,
                        Set<TerrainType> terrainRequirements, int capacity, int minSpacing,
                        boolean walkableRequired, boolean interactionAccessRequired,
                        boolean movementAllowed, int priority) { }
    private record ZoneCandidate(WorldChunk chunk, Zone zone, int tileX, int tileY) { }
    private record ScoredCandidate(ZoneCandidate candidate, long score) { }
}
