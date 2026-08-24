package com.projecteden.world.npc;

import com.projecteden.world.domain.World;
import com.projecteden.world.ecology.TerrainType;
import com.projecteden.world.ecology.WorldCoordinates;
import com.projecteden.world.ecology.WorldHubLayout;
import com.projecteden.world.ecology.WorldPlacedObject;
import com.projecteden.world.ecology.WorldPlacedObjectRepository;
import com.projecteden.world.ecology.WorldPlayerPositionRepository;
import com.projecteden.world.ecology.WorldTerrainTile;
import com.projecteden.world.ecology.WorldTerrainTileRepository;
import com.projecteden.world.repository.WorldRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NpcRuntimeService {
    public static final Duration CHECKPOINT_CADENCE = Duration.ofSeconds(5);
    private static final int MAX_CATCH_UP_STEPS = 1;
    private static final int HUB_MIN_X = 0;
    private static final int HUB_MAX_X = 23;
    private static final int HUB_MIN_Y = 0;
    private static final int HUB_MAX_Y = 15;

    private final NpcRuntimeStateRepository states;
    private final WorldPlacedObjectRepository objects;
    private final WorldTerrainTileRepository terrain;
    private final WorldPlayerPositionRepository players;
    private final WorldRepository worlds;
    private final NpcDialogueSessionRepository sessions;
    private final NpcScheduleRegistry schedules;
    private final Clock clock;

    public NpcRuntimeService(
            NpcRuntimeStateRepository states,
            WorldPlacedObjectRepository objects,
            WorldTerrainTileRepository terrain,
            WorldPlayerPositionRepository players,
            WorldRepository worlds,
            NpcDialogueSessionRepository sessions,
            NpcScheduleRegistry schedules,
            Clock clock) {
        this.states = states;
        this.objects = objects;
        this.terrain = terrain;
        this.players = players;
        this.worlds = worlds;
        this.sessions = sessions;
        this.schedules = schedules;
        this.clock = clock;
    }

    @Transactional
    public List<NpcProjection> ensureForWorld(World world) {
        return ensureForWorldWithResult(world).projections();
    }

    @Transactional
    public EnsureResult ensureForWorldWithResult(World world) {
        LocalDateTime now = utcNow();
        String dateKey = dateKey(now);
        Map<Long, NpcRuntimeState> existing = new HashMap<>();
        states.findByWorldIdOrderByNpcObjectIdAsc(world.getId())
                .forEach(state -> existing.put(state.getNpcObject().getId(), state));
        List<WorldPlacedObject> canonical = objects
                .findByCharacterIdOrderByIdAsc(world.getCharacter().getId()).stream()
                .filter(object -> CanonicalNpcKey.from(object.getAssetType()) != null)
                .sorted(Comparator.comparing(WorldPlacedObject::getId))
                .toList();
        int createdCount = 0;
        for (WorldPlacedObject object : canonical) {
            if (existing.containsKey(object.getId())) continue;
            CanonicalNpcKey key = CanonicalNpcKey.from(object.getAssetType());
            NpcRuntimeState created = states.save(NpcRuntimeState.create(
                    world,
                    object,
                    key,
                    WorldCoordinates.pixelToTile(object.getPositionX()),
                    WorldCoordinates.pixelToTile(object.getPositionY()),
                    dateKey,
                    now));
            existing.put(object.getId(), created);
            createdCount++;
        }
        List<NpcProjection> projections = existing.values().stream()
                .sorted(Comparator.comparing(state -> state.getNpcObject().getId()))
                .map(this::projection)
                .toList();
        return new EnsureResult(projections, createdCount);
    }

    @Transactional(readOnly = true)
    public List<NpcProjection> projections(Long worldId) {
        return states.findByWorldIdOrderByNpcObjectIdAsc(worldId).stream()
                .map(this::projection)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean hasCanonicalRuntime(Long worldId) {
        return states.countByWorldId(worldId) >= CanonicalNpcKey.values().length;
    }

    @Transactional
    public CheckpointResult checkpointWorld(Long worldId) {
        World world = worlds.findByIdForUpdate(worldId)
                .orElseThrow(() -> new IllegalArgumentException("WORLD_NOT_FOUND"));
        ensureForWorld(world);
        LocalDateTime now = utcNow();
        List<NpcRuntimeState> runtime = states.findByWorldIdForUpdate(worldId);
        if (runtime.isEmpty()) return new CheckpointResult(0, 0, dateKey(now));
        LocalDateTime newest = runtime.stream()
                .map(NpcRuntimeState::getLastCheckpointAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (newest != null && Duration.between(newest, now).compareTo(CHECKPOINT_CADENCE) < 0) {
            return new CheckpointResult(runtime.size(), 0, dateKey(now));
        }

        Long characterId = world.getCharacter().getId();
        Map<String, WorldTerrainTile> tiles = terrain
                .findByCharacterIdAndXBetweenAndYBetweenOrderByYAscXAsc(
                        characterId, HUB_MIN_X, HUB_MAX_X, HUB_MIN_Y, HUB_MAX_Y)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        tile -> key(tile.getX(), tile.getY()),
                        tile -> tile));
        Set<String> blockedObjects = objects.findByCharacterIdAndPixelRangeOrderByIdAsc(
                        characterId,
                        WorldCoordinates.tileToPixel(HUB_MIN_X),
                        WorldCoordinates.tileToPixel(HUB_MAX_X),
                        WorldCoordinates.tileToPixel(HUB_MIN_Y),
                        WorldCoordinates.tileToPixel(HUB_MAX_Y))
                .stream()
                .filter(object -> CanonicalNpcKey.from(object.getAssetType()) == null)
                .map(object -> key(
                        WorldCoordinates.pixelToTile(object.getPositionX()),
                        WorldCoordinates.pixelToTile(object.getPositionY())))
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        blockedObjects.add(key(
                WorldHubLayout.COMMUNITY_HOUSE_ANCHOR_X,
                WorldHubLayout.COMMUNITY_HOUSE_ANCHOR_Y));
        blockedObjects.add(key(
                WorldHubLayout.COMMUNITY_HOUSE_APPROACH_X,
                WorldHubLayout.COMMUNITY_HOUSE_APPROACH_Y));
        for (int[] direction : CARDINAL_DIRECTIONS) {
            blockedObjects.add(key(
                    WorldHubLayout.COMMUNITY_HOUSE_APPROACH_X + direction[0],
                    WorldHubLayout.COMMUNITY_HOUSE_APPROACH_Y + direction[1]));
        }
        Set<String> occupiedNpcs = runtime.stream()
                .map(state -> key(state.getTileX(), state.getTileY()))
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        Set<String> playerTiles = new HashSet<>();
        players.findByCharacterId(characterId)
                .ifPresent(player -> playerTiles.add(key(player.getX(), player.getY())));
        Set<Long> dialogueLockedNpcIds = new HashSet<>(
                sessions.findActiveNpcObjectIds(worldId, now));

        int moved = 0;
        for (NpcRuntimeState state : runtime) {
            NpcScheduleRegistry.ResolvedSchedule slot = schedules.resolve(state.getNpcKey(), now);
            String current = key(state.getTileX(), state.getTileY());
            occupiedNpcs.remove(current);
            int nextX = state.getTileX();
            int nextY = state.getTileY();
            if (!dialogueLockedNpcIds.contains(state.getNpcObject().getId())) {
                for (int[] candidate : candidates(state, slot)) {
                    if (validDestination(
                            world, tiles, blockedObjects, occupiedNpcs, playerTiles,
                            candidate[0], candidate[1])) {
                        nextX = candidate[0];
                        nextY = candidate[1];
                        break;
                    }
                }
            }
            if (nextX != state.getTileX() || nextY != state.getTileY()) moved++;
            state.checkpoint(
                    nextX,
                    nextY,
                    nextX == slot.destinationX() && nextY == slot.destinationY()
                            ? slot.activity() : NpcActivity.WALKING,
                    slot.slotKey(),
                    dateKey(now),
                    now);
            occupiedNpcs.add(key(nextX, nextY));
        }
        return new CheckpointResult(runtime.size(), moved, dateKey(now));
    }

    public NpcProjection projection(NpcRuntimeState state) {
        NpcScheduleRegistry.ResolvedSchedule slot = schedules.resolve(state.getNpcKey(), utcNow());
        return new NpcProjection(
                state.getNpcObject().getId(),
                state.getNpcKey().name(),
                state.getNpcKey().displayNameKey(),
                displayName(state.getNpcKey()),
                state.getNpcKey().spriteKey(),
                state.getNpcKey().portraitKey(),
                state.getTileX(),
                state.getTileY(),
                WorldCoordinates.tileToPixel(state.getTileX()),
                WorldCoordinates.tileToPixel(state.getTileY()),
                state.getActivity(),
                state.getScheduleSlot(),
                slot.interactionEnabled(),
                slot.dialogueKey(),
                state.getStateVersion());
    }

    public boolean occupies(Long worldId, int tileX, int tileY) {
        return states.findByWorldIdOrderByNpcObjectIdAsc(worldId).stream()
                .anyMatch(state -> state.getTileX() == tileX && state.getTileY() == tileY);
    }

    public NpcRuntimeState requireRuntime(Long worldId, Long objectId) {
        NpcRuntimeState state = states.findByNpcObjectId(objectId)
                .orElseThrow(() -> new IllegalArgumentException("NPC_NOT_FOUND"));
        if (!state.getWorld().getId().equals(worldId)) throw new IllegalArgumentException("NPC_NOT_OWNED");
        return state;
    }

    private List<int[]> candidates(
            NpcRuntimeState state,
            NpcScheduleRegistry.ResolvedSchedule slot) {
        List<int[]> result = new ArrayList<>(6);
        int deltaX = Integer.compare(slot.destinationX(), state.getTileX());
        int deltaY = Integer.compare(slot.destinationY(), state.getTileY());
        boolean xFirst = Math.floorMod(state.getNpcObject().getId().intValue(), 2) == 0;
        if (xFirst) {
            if (deltaX != 0) result.add(new int[]{state.getTileX() + deltaX, state.getTileY()});
            if (deltaY != 0) result.add(new int[]{state.getTileX(), state.getTileY() + deltaY});
        } else {
            if (deltaY != 0) result.add(new int[]{state.getTileX(), state.getTileY() + deltaY});
            if (deltaX != 0) result.add(new int[]{state.getTileX() + deltaX, state.getTileY()});
        }
        if (isCommunityApproachConflict(state.getTileX(), state.getTileY())) {
            for (int[] direction : CARDINAL_DIRECTIONS) {
                int candidateX = state.getTileX() + direction[0];
                int candidateY = state.getTileY() + direction[1];
                if (!isCommunityApproachConflict(candidateX, candidateY)) {
                    result.add(new int[]{candidateX, candidateY});
                }
            }
        }
        return result;
    }

    private static boolean validDestination(
            World world,
            Map<String, WorldTerrainTile> tiles,
            Set<String> blockedObjects,
            Set<String> occupiedNpcs,
            Set<String> playerTiles,
            int x,
            int y) {
        if (!world.containsTile(x, y)) return false;
        WorldTerrainTile tile = tiles.get(key(x, y));
        if (tile == null || !tile.isWalkable() || tile.getTerrainType() == TerrainType.SOIL) return false;
        String key = key(x, y);
        return !WorldHubLayout.isNpcProtectedTile(x, y)
                && !blockedObjects.contains(key) && !occupiedNpcs.contains(key) && !playerTiles.contains(key);
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String dateKey(LocalDateTime now) {
        return now.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private static String key(int x, int y) { return x + ":" + y; }
    private static boolean isCommunityApproachConflict(int x, int y) {
        return Math.abs(x - WorldHubLayout.COMMUNITY_HOUSE_APPROACH_X)
                + Math.abs(y - WorldHubLayout.COMMUNITY_HOUSE_APPROACH_Y) <= 1;
    }
    private static final int[][] CARDINAL_DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static String displayName(CanonicalNpcKey key) {
        return switch (key) {
            case NPC_MAYOR -> "마을 안내자";
            case NPC_GARDENER -> "정원 관리인";
            case NPC_RESEARCHER -> "기억 보관인";
            case NPC_CARETAKER -> "동물 돌봄이";
        };
    }

    public record CheckpointResult(int npcCount, int movedCount, String scheduleDateKey) { }
    public record EnsureResult(List<NpcProjection> projections, int createdCount) { }
}
