package com.projecteden.world.ecology;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.projecteden.ai.domain.Recognition;
import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;
import com.projecteden.world.chunk.WorldChunkManager;
import com.projecteden.world.generation.ChunkDiscoveryService;
import com.projecteden.world.generation.ChunkGenerationService;
import com.projecteden.world.npc.CanonicalNpcKey;
import com.projecteden.world.npc.NpcProjection;
import com.projecteden.world.npc.NpcRuntimeService;
import com.projecteden.world.npc.NpcProgressNotification;
import com.projecteden.world.npc.NpcQuestEventType;
import com.projecteden.world.npc.NpcRelationshipService;

@Service
public class WorldEcologyService {
    private static final int TEMPLATE_VERSION = 3;
    private static final Duration ANIMAL_MOVEMENT_INTERVAL = Duration.ofSeconds(30);
    private static final int MAX_ANIMALS_MOVED_PER_TICK = 8;
    private final WorldChangeRepository changes; private final WorldPlacedObjectRepository objects; private final CharacterRepository characters; private final WorldTerrainTileRepository terrain; private final WorldPlayerPositionRepository positions; private final WorldRepository worlds;
    private final Clock clock;
    private final WorldChunkManager chunkManager;
    private final ChunkGenerationService generation;
    private final ChunkDiscoveryService discovery;
    private final NpcRuntimeService npcRuntime;
    private final NpcRelationshipService npcRelationships;
    private final PhotoEcologyPlacementService ecologyPlacement;
    private final WorldTerrainBatchWriter terrainWriter;
    public WorldEcologyService(
            WorldChangeRepository changes,
            WorldPlacedObjectRepository objects,
            CharacterRepository characters,
            WorldTerrainTileRepository terrain,
            WorldPlayerPositionRepository positions,
            WorldRepository worlds,
            Clock clock,
            WorldChunkManager chunkManager,
            ChunkGenerationService generation,
            ChunkDiscoveryService discovery,
            NpcRuntimeService npcRuntime,
            NpcRelationshipService npcRelationships,
            PhotoEcologyPlacementService ecologyPlacement,
            WorldTerrainBatchWriter terrainWriter) {
        this.changes=changes; this.objects=objects; this.characters=characters;
        this.terrain=terrain; this.positions=positions; this.worlds=worlds;
        this.clock=clock; this.chunkManager=chunkManager;
        this.generation=generation; this.discovery=discovery;
        this.npcRuntime=npcRuntime;
        this.npcRelationships=npcRelationships;
        this.ecologyPlacement=ecologyPlacement;
        this.terrainWriter=terrainWriter;
    }
    @Transactional public WorldChangeResult createFor(Recognition recognition) {
        WorldChange existing = changes.findByRecognitionId(recognition.getId()).orElse(null);
        if (existing != null) return result(existing);
        if (worlds.findByCharacterId(recognition.getPhoto().getCharacter().getId()).isEmpty()) return null;
        return create(recognition);
    }
    @Transactional(readOnly=true) public WorldChangeResult findFor(Long recognitionId) { return changes.findByRecognitionId(recognitionId).map(this::result).orElse(null); }
    @Transactional public WorldStateResponse stateForUser(Long userId) {
        var character=characters.findByUserId(userId).orElseThrow(()->new ResourceNotFoundException("캐릭터를 찾을 수 없습니다."));
        World world = worldForCharacter(character);
        boolean mutationRequired = !isCanonicalReadReady(character.getId(), world)
                || animalMovementDue(world);
        if (mutationRequired) {
            world = lockWorld(world);
            if (!isCanonicalReadReady(character.getId(), world)) {
                bootstrap(character, world);
                chunkManager.ensureHubChunks(world);
            }
        }
        var position=position(character, world);
        Long characterId=character.getId();
        NpcRuntimeService.EnsureResult npcEnsure = mutationRequired
                ? npcRuntime.ensureForWorldWithResult(world)
                : new NpcRuntimeService.EnsureResult(npcRuntime.projections(world.getId()), 0);
        List<NpcProjection> runtimeNpcs = npcEnsure.projections();
        if (mutationRequired) moveAnimalsIfDue(characterId, position, world, runtimeNpcs);
        if (npcEnsure.createdCount() > 0) {
            npcRelationships.replayPending(userId);
        }
        var worldChanges=changes.findByCharacterIdOrderByIdAsc(characterId);
        Set<Long> replacedObjectIds = worldChanges.stream()
                .map(WorldChange::getTargetObject)
                .filter(java.util.Objects::nonNull)
                .map(WorldPlacedObject::getId)
                .collect(Collectors.toSet());
        var placedObjects = objects.findByCharacterIdOrderByIdAsc(characterId).stream()
                .filter(object -> !replacedObjectIds.contains(object.getId()))
                .toList();
        var placed = placedObjects.stream().map(PlacedObjectResponse::from).toList();
        Map<Long, List<Long>> objectIdsByChange = placedObjects.stream().collect(Collectors.groupingBy(
                object -> object.getWorldChange().getId(),
                Collectors.mapping(WorldPlacedObject::getId, Collectors.toList())));
        Map<String, List<PlacedObjectResponse>> contextualByTile = placed.stream()
                .filter(object -> contextualInteraction(object.assetType()) != null)
                .collect(Collectors.groupingBy(object -> pixelKey(object.x(), object.y())));
        Map<Long, WorldAssetType> npcAssets = placed.stream()
                .filter(object -> isTemplateNpc(object.assetType()))
                .collect(Collectors.toMap(PlacedObjectResponse::id, PlacedObjectResponse::assetType));
        var npcPositions = runtimeNpcs.stream()
                .map(npc -> NpcPositionResponse.from(npc, npcAssets.get(npc.objectId())))
                .toList();
        Map<String, List<PlacedObjectResponse>> npcByTile = npcPositions.stream().collect(Collectors.groupingBy(
                npc -> pixelKey(npc.pixelX(), npc.pixelY()),
                Collectors.mapping(npc -> new PlacedObjectResponse(
                        npc.id(), npc.assetType(), WorldCategory.MEMORY,
                        npc.pixelX(), npc.pixelY(), TerrainType.GRASS,
                        HabitatType.DECORATION_ONLY, null, npc.pixelY(), 0),
                        Collectors.toList())));
        var interactions=java.util.stream.Stream.of(new int[]{0,-1},new int[]{0,1},new int[]{-1,0},new int[]{1,0})
                .map(d->terrain.findByCharacterIdAndXAndY(characterId,position.getX()+d[0],position.getY()+d[1]).map(t->{
                    String key = pixelKey(
                            WorldCoordinates.tileToPixel(t.getX()),
                            WorldCoordinates.tileToPixel(t.getY()));
                    var npc = firstDeterministic(npcByTile.get(key));
                    if (npc != null) {
                        return new TileInteractionResponse(t.getX(),t.getY(),TileInteractionType.TALK,true,null,
                                npc.id(),npc.assetType(),npcName(npc.assetType()));
                    }
                    var contextual = firstDeterministic(contextualByTile.get(key));
                    if (contextual != null) {
                        ContextualInteraction metadata = contextualInteraction(contextual.assetType());
                        if (!contextualInteractionAllowed(
                                contextual.assetType(), position.getX(), position.getY(), t.getX(), t.getY())) {
                            return new TileInteractionResponse(t.getX(),t.getY(),TileInteractionType.INSPECT,true,null);
                        }
                        return new TileInteractionResponse(t.getX(),t.getY(),TileInteractionType.INTERACT,true,null,
                                contextual.id(),contextual.assetType(),metadata.displayName(),metadata.category(),metadata.actionLabel());
                    }
                    return new TileInteractionResponse(t.getX(),t.getY(),TileInteractionType.INSPECT,true,null);
                })).flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparingInt((TileInteractionResponse interaction) -> interactionPriority(interaction.type()))
                        .thenComparingInt(TileInteractionResponse::y)
                        .thenComparingInt(TileInteractionResponse::x)
                        .thenComparing(interaction -> interaction.targetAssetType() == null ? "" : interaction.targetAssetType().name())
                        .thenComparing(interaction -> interaction.targetId() == null ? Long.MAX_VALUE : interaction.targetId()))
                .toList();
        return new WorldStateResponse(
                world.getId(),
                worldChanges.stream().map(change -> result(
                        change, objectIdsByChange.getOrDefault(change.getId(), List.of()))).toList(),
                terrain.findByCharacterIdOrderByYAscXAsc(characterId).stream()
                        .map(t -> new TerrainTileResponse(t.getX(), t.getY(), t.getTerrainType(), t.isWalkable()))
                        .toList(),
                placed,
                bounds(world),
                new PlayerPositionResponse(position.getX(), position.getY()),
                interactions,
                npcPositions,
                List.of(),
                List.of(),
                "LIVING_VILLAGE",
                character.getName() + "의 마을",
                WorldCoordinates.TILE_SIZE,
                world.getWorldGenerationVersion());
    }

    /**
     * Synchronizes the same authoritative world checkpoint as /state without
     * materializing full terrain/object arrays. Chunk reads then use two
     * bounded range queries for their payload.
     */
    @Transactional
    public WorldChunkReadContext chunkReadContextForUser(Long userId) {
        var character = characters.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("캐릭터를 찾을 수 없습니다."));
        World world = worldForCharacter(character);
        if (!isCanonicalReadReady(character.getId(), world)) {
            world = lockWorld(world);
            bootstrap(character, world);
            chunkManager.ensureHubChunks(world);
            npcRuntime.ensureForWorld(world);
        }
        var player = position(character, world);
        Long characterId = character.getId();
        List<NpcProjection> runtimeNpcs = npcRuntime.projections(world.getId());

        int minX = Math.max(world.getMinTileX(), player.getX() - 1);
        int maxX = Math.min(world.getMaxTileX(), player.getX() + 1);
        int minY = Math.max(world.getMinTileY(), player.getY() - 1);
        int maxY = Math.min(world.getMaxTileY(), player.getY() + 1);
        Map<String, List<PlacedObjectResponse>> adjacentObjects = objects
                .findByCharacterIdAndPixelRangeOrderByIdAsc(
                        characterId,
                        WorldCoordinates.minPixelForTile(minX),
                        WorldCoordinates.maxPixelForTile(maxX),
                        WorldCoordinates.minPixelForTile(minY),
                        WorldCoordinates.maxPixelForTile(maxY))
                .stream()
                .map(PlacedObjectResponse::from)
                .collect(Collectors.groupingBy(object -> pixelKey(object.x(), object.y())));
        Map<Long, WorldPlacedObject> canonicalObjects = objects.findByCharacterIdOrderByIdAsc(characterId).stream()
                .filter(object -> CanonicalNpcKey.from(object.getAssetType()) != null)
                .collect(Collectors.toMap(WorldPlacedObject::getId, object -> object));
        List<NpcPositionResponse> npcPositions = runtimeNpcs.stream()
                .map(npc -> NpcPositionResponse.from(
                        npc, canonicalObjects.get(npc.objectId()).getAssetType()))
                .toList();
        npcPositions.stream()
                .filter(npc -> npc.x() >= minX && npc.x() <= maxX && npc.y() >= minY && npc.y() <= maxY)
                .forEach(npc -> adjacentObjects.computeIfAbsent(
                        pixelKey(npc.pixelX(), npc.pixelY()), ignored -> new java.util.ArrayList<>())
                        .add(new PlacedObjectResponse(
                                npc.id(), npc.assetType(), WorldCategory.MEMORY,
                                npc.pixelX(), npc.pixelY(), TerrainType.GRASS,
                                HabitatType.DECORATION_ONLY, null, npc.pixelY(), 0)));
        Map<String, WorldTerrainTile> adjacentTerrain = terrain
                .findByCharacterIdAndXBetweenAndYBetweenOrderByYAscXAsc(
                        characterId, minX, maxX, minY, maxY)
                .stream()
                .collect(Collectors.toMap(tile -> tile.getX() + ":" + tile.getY(), tile -> tile));
        List<TileInteractionResponse> interactions = java.util.stream.Stream
                .of(new int[]{0,-1}, new int[]{0,1}, new int[]{-1,0}, new int[]{1,0})
                .map(delta -> adjacentTerrain.get(
                        (player.getX() + delta[0]) + ":" + (player.getY() + delta[1])))
                .filter(java.util.Objects::nonNull)
                .map(tile -> interactionAt(tile, adjacentObjects.get(pixelKey(
                        WorldCoordinates.tileToPixel(tile.getX()),
                        WorldCoordinates.tileToPixel(tile.getY()))), player.getX(), player.getY()))
                .sorted(Comparator.comparingInt((TileInteractionResponse interaction) ->
                                interactionPriority(interaction.type()))
                        .thenComparingInt(TileInteractionResponse::y)
                        .thenComparingInt(TileInteractionResponse::x)
                        .thenComparing(interaction ->
                                interaction.targetAssetType() == null ? "" : interaction.targetAssetType().name())
                        .thenComparing(interaction ->
                                interaction.targetId() == null ? Long.MAX_VALUE : interaction.targetId()))
                .toList();
        return new WorldChunkReadContext(
                world, characterId,
                new PlayerPositionResponse(player.getX(), player.getY()),
                interactions,
                npcPositions);
    }

    private boolean isCanonicalReadReady(Long characterId, World world) {
        return world.getVillageTemplateVersion() >= TEMPLATE_VERSION
                && terrain.countByCharacterIdAndXBetweenAndYBetween(characterId, 0, 23, 0, 15) == 384
                && chunkManager.hasCanonicalHub(world)
                && npcRuntime.hasCanonicalRuntime(world.getId())
                && positions.existsByCharacterId(characterId);
    }

    private boolean animalMovementDue(World world) {
        LocalDateTime lastMovedAt = world.getLastAnimalMovementAt();
        return lastMovedAt == null
                || Duration.between(lastMovedAt, LocalDateTime.now(clock))
                        .compareTo(ANIMAL_MOVEMENT_INTERVAL) >= 0;
    }

    private static TileInteractionResponse interactionAt(
            WorldTerrainTile tile,
            List<PlacedObjectResponse> candidates,
            int playerX,
            int playerY) {
        PlacedObjectResponse npc = firstDeterministic(candidates == null ? null : candidates.stream()
                .filter(object -> isTemplateNpc(object.assetType())).toList());
        if (npc != null) {
            return new TileInteractionResponse(
                    tile.getX(), tile.getY(), TileInteractionType.TALK, true, null,
                    npc.id(), npc.assetType(), npcName(npc.assetType()));
        }
        PlacedObjectResponse contextual = firstDeterministic(candidates == null ? null : candidates.stream()
                .filter(object -> contextualInteraction(object.assetType()) != null).toList());
        if (contextual != null) {
            ContextualInteraction metadata = contextualInteraction(contextual.assetType());
            if (!contextualInteractionAllowed(
                    contextual.assetType(), playerX, playerY, tile.getX(), tile.getY())) {
                return new TileInteractionResponse(
                        tile.getX(), tile.getY(), TileInteractionType.INSPECT, true, null);
            }
            return new TileInteractionResponse(
                    tile.getX(), tile.getY(), TileInteractionType.INTERACT, true, null,
                    contextual.id(), contextual.assetType(), metadata.displayName(),
                    metadata.category(), metadata.actionLabel());
        }
        return new TileInteractionResponse(
                tile.getX(), tile.getY(), TileInteractionType.INSPECT, true, null);
    }
    @Transactional
    public MoveResponse move(Long userId, MoveRequest request) {
        var character = characters.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("캐릭터를 찾을 수 없습니다."));
        World world = worldForCharacter(character);
        world = lockWorld(world);
        // Movement is already serialized by the world lock and generates the
        // destination chunk below. Do not rerun the multi-query read-readiness
        // audit for every single tile of an already bootstrapped world.
        if (world.getVillageTemplateVersion() < TEMPLATE_VERSION) {
            bootstrap(character, world);
            chunkManager.ensureHubChunks(world);
        }
        var current = position(character, world);
        if (Math.abs(request.targetX() - current.getX()) + Math.abs(request.targetY() - current.getY()) > 1) {
            return new MoveResponse(false, current.getX(), current.getY(), null, "MOVE_TOO_FAR");
        }
        if (!world.containsTile(request.targetX(), request.targetY())) {
            return new MoveResponse(false, current.getX(), current.getY(), null, "OUT_OF_BOUNDS");
        }
        int currentChunkX = WorldCoordinates.tileToChunk(current.getX());
        int currentChunkY = WorldCoordinates.tileToChunk(current.getY());
        int targetChunkX = WorldCoordinates.tileToChunk(request.targetX());
        int targetChunkY = WorldCoordinates.tileToChunk(request.targetY());
        try {
            generation.ensureGenerated(world.getId(), targetChunkX, targetChunkY);
        } catch (IllegalArgumentException | IllegalStateException generationFailure) {
            return new MoveResponse(false, current.getX(), current.getY(), null, "CHUNK_UNAVAILABLE");
        }
        var tile = terrain.findByCharacterIdAndXAndY(character.getId(), request.targetX(), request.targetY())
                .orElseThrow();
        if (!tile.isWalkable()) {
            return new MoveResponse(false, current.getX(), current.getY(), tile.getTerrainType(), "TERRAIN_BLOCKED");
        }
        if (npcRuntime.occupies(world.getId(), request.targetX(), request.targetY())) {
            return new MoveResponse(false, current.getX(), current.getY(), tile.getTerrainType(), "NPC_BLOCKED");
        }
        current.moveTo(request.targetX(), request.targetY());
        var discovered = discovery.discover(world.getId(), targetChunkX, targetChunkY);
        boolean enteredChunk = currentChunkX != targetChunkX || currentChunkY != targetChunkY;
        if (enteredChunk && (targetChunkX != 0 || targetChunkY != 0)) {
            npcRelationships.recordEvent(
                    userId,
                    "VISIT:" + world.getId() + ":" + targetChunkX + ":" + targetChunkY,
                    NpcQuestEventType.VISIT_LOCATION,
                    "OUTER_REGION");
        }
        return new MoveResponse(
                true, current.getX(), current.getY(), tile.getTerrainType(), "OK",
                enteredChunk, targetChunkX, targetChunkY, discovered.newlyDiscovered(),
                discovered.chunk().getRegionType(),
                "region." + discovered.chunk().getRegionType().name().toLowerCase(java.util.Locale.ROOT));
    }

    @Transactional
    public List<NpcProgressNotification> recordInteraction(Long userId, Long targetId) {
        WorldStateResponse state = stateForUser(userId);
        TileInteractionResponse interaction = state.availableInteractions().stream()
                .filter(candidate -> targetId.equals(candidate.targetId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("INTERACTION_NOT_AVAILABLE"));
        NpcQuestEventType type;
        String target;
        if (interaction.category() == TileInteractionCategory.ANIMAL) {
            type = NpcQuestEventType.ANIMAL_INTERACTION;
            target = interaction.targetAssetType() == null ? null : interaction.targetAssetType().name();
        } else if (interaction.category() == TileInteractionCategory.COMMUNITY) {
            type = NpcQuestEventType.COMMUNITY_VISIT;
            target = "COMMUNITY_HOUSE";
        } else {
            type = NpcQuestEventType.INSPECT;
            target = interaction.category() == TileInteractionCategory.FARM
                    ? "FARM"
                    : interaction.targetAssetType() == null ? null : interaction.targetAssetType().name();
        }
        return npcRelationships.recordEvent(
                userId,
                "INTERACTION:" + type + ":" + targetId,
                type,
                target);
    }

    WorldPlayerPosition position(com.projecteden.character.domain.Character character, World world) {
        return positions.findByCharacterId(character.getId()).orElseGet(() -> positions.save(
                WorldPlayerPosition.create(
                        character,
                        clamp(11, world.getMinTileX(), world.getMaxTileX()),
                        clamp(8, world.getMinTileY(), world.getMaxTileY()))));
    }

    WorldPlayerPosition position(com.projecteden.character.domain.Character character) {
        return position(character, worldForCharacter(character));
    }

    private void bootstrap(com.projecteden.character.domain.Character character, World world) {
        if (!terrain.existsByCharacterId(character.getId())) {
            List<WorldTerrainBatchWriter.TerrainSeed> initialTerrain = new ArrayList<>(384);
            for (int y = 0; y <= 15; y++) {
                for (int x = 0; x <= 23; x++) {
                    initialTerrain.add(new WorldTerrainBatchWriter.TerrainSeed(x, y, baseTerrainAt(x, y)));
                }
            }
            terrainWriter.insertMissing(character.getId(), initialTerrain);
        }
        seedTemplate(character);
        reconcileHubLayout(character);
    }

    private World lockWorld(World world) {
        return worlds.findByIdForUpdate(world.getId())
                .orElseThrow(() -> new ResourceNotFoundException("월드를 찾을 수 없습니다."));
    }
    private void seedTemplate(com.projecteden.character.domain.Character character) {
        var world = worlds.findByCharacterId(character.getId()).orElse(null);
        if (world != null && world.getVillageTemplateVersion() >= TEMPLATE_VERSION) return;
        soil(character, 2, 9, 4, 2); // empty plot
        soil(character, 2, 12, 4, 2); // carrot rows
        soil(character, 3, 4, 4, 2); // flower rows
        soil(character, 7, 10, 4, 2); // mixed vegetables
        template(character,"TEMPLATE_PLAZA",WorldCategory.MEMORY,WorldAssetType.PLAZA,11,7);
        template(character,"TEMPLATE_FARM_EMPTY",WorldCategory.NATURE,WorldAssetType.FARM_PLOT_EMPTY,3,9);
        cropRow(character,"TEMPLATE_CARROT",WorldCategory.FOOD,WorldAssetType.FARM_CARROT,2,12,4,2);
        cropRow(character,"TEMPLATE_FLOWER",WorldCategory.NATURE,WorldAssetType.FARM_FLOWER,3,4,4,2);
        cropRow(character,"TEMPLATE_TOMATO",WorldCategory.FOOD,WorldAssetType.FARM_TOMATO,7,10,2,2);
        cropRow(character,"TEMPLATE_CABBAGE",WorldCategory.FOOD,WorldAssetType.FARM_CABBAGE,9,10,2,2);
        template(character,"TEMPLATE_HOUSE",WorldCategory.MEMORY,WorldAssetType.COMMUNITY_HOUSE,
                WorldHubLayout.COMMUNITY_HOUSE_ANCHOR_X,WorldHubLayout.COMMUNITY_HOUSE_ANCHOR_Y);
        template(character,"TEMPLATE_GUIDE",WorldCategory.MEMORY,WorldAssetType.DEFAULT_NPC_GUIDE,10,6);
        template(character,"TEMPLATE_GARDENER",WorldCategory.NATURE,WorldAssetType.DEFAULT_NPC_GARDENER,5,8);
        template(character,"TEMPLATE_KEEPER",WorldCategory.MEMORY,WorldAssetType.DEFAULT_NPC_MEMORY_KEEPER,12,9);
        template(character,"TEMPLATE_CARETAKER",WorldCategory.ANIMAL,WorldAssetType.DEFAULT_NPC_ANIMAL_CARETAKER,16,8);
        template(character,"TEMPLATE_DOG",WorldCategory.ANIMAL,WorldAssetType.DEFAULT_DOG,17,9);
        template(character,"TEMPLATE_CAT",WorldCategory.ANIMAL,WorldAssetType.DEFAULT_CAT,18,9);
        template(character,"TEMPLATE_BIRD_A",WorldCategory.ANIMAL,WorldAssetType.DEFAULT_BIRD,19,8);
        template(character,"TEMPLATE_BIRD_B",WorldCategory.ANIMAL,WorldAssetType.DEFAULT_BIRD,20,8);
        if (world != null) world.applyVillageTemplateVersion(TEMPLATE_VERSION);
    }
    private void template(com.projecteden.character.domain.Character character,String key,WorldCategory category,WorldAssetType asset,int tileX,int tileY) {
        if(changes.findByCharacterIdAndMessageKey(character.getId(),key).isPresent()) return;
        int pixelX = WorldCoordinates.tileToPixel(tileX);
        int pixelY = WorldCoordinates.tileToPixel(tileY);
        var change=changes.save(WorldChange.template(character,category,asset,key,"기본 마을 풍경",pixelX,pixelY));
        objects.save(WorldPlacedObject.create(change,asset,TerrainType.GRASS,habitatFor(asset),pixelX,pixelY));
    }
    private void reconcileHubLayout(com.projecteden.character.domain.Character character) {
        for (int x = WorldHubLayout.BRIDGE_MIN_X; x <= WorldHubLayout.BRIDGE_MAX_X; x++) {
            changeTerrain(character.getId(), x, WorldHubLayout.BRIDGE_Y, TerrainType.BRIDGE);
        }
        for (int y = 11; y <= 15; y++) {
            changeTerrain(character.getId(), WorldHubLayout.BRIDGE_EXIT_X, y,
                    y == WorldHubLayout.BRIDGE_Y ? TerrainType.ROAD : TerrainType.GRASS);
        }
        changeTerrain(character.getId(),
                WorldHubLayout.COMMUNITY_HOUSE_ANCHOR_X,
                WorldHubLayout.COMMUNITY_HOUSE_ANCHOR_Y,
                TerrainType.ROAD);
        changes.findByCharacterIdAndMessageKey(character.getId(), "TEMPLATE_HOUSE")
                .ifPresent(change -> {
                    int x = WorldCoordinates.tileToPixel(WorldHubLayout.COMMUNITY_HOUSE_ANCHOR_X);
                    int y = WorldCoordinates.tileToPixel(WorldHubLayout.COMMUNITY_HOUSE_ANCHOR_Y);
                    change.moveFocus(x, y);
                    objects.findByWorldChangeId(change.getId()).stream()
                            .filter(object -> object.getAssetType() == WorldAssetType.COMMUNITY_HOUSE)
                            .forEach(object -> object.moveTo(TerrainType.ROAD, x, y));
                });
    }
    private void changeTerrain(Long characterId, int x, int y, TerrainType type) {
        terrain.findByCharacterIdAndXAndY(characterId, x, y)
                .ifPresent(tile -> tile.changeTerrain(type));
    }
    private void cropRow(com.projecteden.character.domain.Character c,String prefix,WorldCategory category,WorldAssetType asset,int x,int y,int width,int height){ for(int row=0;row<height;row++)for(int column=0;column<width;column++)template(c,prefix+"_"+row+"_"+column,category,asset,x+column,y+row); }
    private void soil(com.projecteden.character.domain.Character c,int x,int y,int width,int height){ for(int row=0;row<height;row++)for(int column=0;column<width;column++)terrain.findByCharacterIdAndXAndY(c.getId(),x+column,y+row).ifPresent(tile -> { if(tile.getTerrainType()==TerrainType.GRASS)tile.changeTerrain(TerrainType.SOIL); }); }
    private static String pixelKey(int x, int y) { return x + ":" + y; }
    private static PlacedObjectResponse firstDeterministic(List<PlacedObjectResponse> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        return candidates.stream()
                .sorted(Comparator.comparing((PlacedObjectResponse object) -> object.assetType().name())
                        .thenComparing(PlacedObjectResponse::id))
                .findFirst()
                .orElse(null);
    }
    private static int interactionPriority(TileInteractionType type) {
        return switch (type) {
            case TALK -> 0;
            case INTERACT -> 1;
            case INSPECT -> 2;
        };
    }
    private static ContextualInteraction contextualInteraction(WorldAssetType type) {
        return switch (type) {
            case FARM_PLOT_EMPTY -> new ContextualInteraction(TileInteractionCategory.FARM, "비어 있는 밭", "살펴보기");
            case FARM_CARROT -> new ContextualInteraction(TileInteractionCategory.CROP, "당근밭", "작물 살펴보기");
            case FARM_FLOWER -> new ContextualInteraction(TileInteractionCategory.CROP, "꽃밭", "작물 살펴보기");
            case FARM_VEGETABLE -> new ContextualInteraction(TileInteractionCategory.CROP, "채소밭", "작물 살펴보기");
            case FARM_TOMATO -> new ContextualInteraction(TileInteractionCategory.CROP, "토마토밭", "작물 살펴보기");
            case FARM_CABBAGE -> new ContextualInteraction(TileInteractionCategory.CROP, "양배추밭", "작물 살펴보기");
            case DEFAULT_DOG -> new ContextualInteraction(TileInteractionCategory.ANIMAL, "강아지", "다가가기");
            case DEFAULT_CAT -> new ContextualInteraction(TileInteractionCategory.ANIMAL, "고양이", "다가가기");
            case DEFAULT_BIRD -> new ContextualInteraction(TileInteractionCategory.ANIMAL, "새", "다가가기");
            case COMMUNITY_HOUSE -> new ContextualInteraction(TileInteractionCategory.COMMUNITY, "마을 회관", "둘러보기");
            default -> null;
        };
    }
    private static boolean contextualInteractionAllowed(
            WorldAssetType type,
            int playerX,
            int playerY,
            int targetX,
            int targetY) {
        if (type != WorldAssetType.COMMUNITY_HOUSE) return true;
        return playerX == WorldHubLayout.COMMUNITY_HOUSE_APPROACH_X
                && playerY == WorldHubLayout.COMMUNITY_HOUSE_APPROACH_Y
                && targetX == WorldHubLayout.COMMUNITY_HOUSE_ANCHOR_X
                && targetY == WorldHubLayout.COMMUNITY_HOUSE_ANCHOR_Y;
    }
    private static boolean isTemplateNpc(WorldAssetType type) { return type == WorldAssetType.DEFAULT_NPC_GUIDE || type == WorldAssetType.DEFAULT_NPC_GARDENER || type == WorldAssetType.DEFAULT_NPC_MEMORY_KEEPER || type == WorldAssetType.DEFAULT_NPC_ANIMAL_CARETAKER; }
    private static String npcName(WorldAssetType type) { return switch (type) { case DEFAULT_NPC_GUIDE -> "마을 안내자"; case DEFAULT_NPC_GARDENER -> "정원 관리인"; case DEFAULT_NPC_MEMORY_KEEPER -> "기억 보관인"; case DEFAULT_NPC_ANIMAL_CARETAKER -> "동물 돌봄이"; default -> "마을 주민"; }; }
    private record ContextualInteraction(TileInteractionCategory category, String displayName, String actionLabel) { }
    private WorldChangeResult create(Recognition recognition) {
        Mapping mapping = mappingFor(recognition.getRecognizedObject());
        var character = recognition.getPhoto().getCharacter();
        World world = worlds.findByCharacterIdForUpdate(character.getId()).orElse(null);
        WorldChange concurrentWinner = changes.findByRecognitionId(recognition.getId()).orElse(null);
        if (concurrentWinner != null) return result(concurrentWinner);
        EcologyPlacementDecision decision;
        if (world == null) {
            decision = ecologyPlacement.unavailable(recognition);
        } else {
            decision = ecologyPlacement.select(recognition, world);
        }
        int x = decision.applied() ? WorldCoordinates.tileToPixel(decision.tileX()) : 0;
        int y = decision.applied() ? WorldCoordinates.tileToPixel(decision.tileY()) : 0;
        WorldChange change = WorldChange.create(character, recognition, mapping.category,
                decision.profile().projectedAssetType(), mapping.key, mapping.message, x, y);
        change.recordEcologyPlacement(
                decision.profile().profileKey(), decision.profile().ecologyCategory(), decision.applied(),
                decision.regionType(), decision.chunkX(), decision.chunkY(), decision.zoneTag(),
                decision.profile().version(), decision.reason(), LocalDateTime.now(clock));
        change = changes.saveAndFlush(change);
        if (decision.applied()) {
            WorldTerrainTile selected = terrain.findByCharacterIdAndXAndY(character.getId(), decision.tileX(), decision.tileY())
                    .orElseThrow(() -> new IllegalStateException("ECOLOGY_SELECTED_TERRAIN_MISSING"));
            objects.saveAndFlush(WorldPlacedObject.create(change, decision.profile().projectedAssetType(),
                    selected.getTerrainType(), decision.profile().movementHabitat(), x, y));
        }
        return result(change);
    }
    WorldChangeResult createTargeted(Recognition recognition, WorldPlacedObject target, WorldAssetType cropAsset) {
        WorldCategory category = cropAsset == WorldAssetType.FARM_FLOWER ? WorldCategory.NATURE : WorldCategory.FOOD;
        String message = cropAsset == WorldAssetType.FARM_FLOWER
                ? "이 기억이 빈 밭에 꽃으로 피어났습니다."
                : "이 기억이 빈 밭에 새로운 작물로 자라났습니다.";
        WorldChange change = changes.saveAndFlush(WorldChange.targeted(
                recognition.getPhoto().getCharacter(), recognition, target, category, cropAsset,
                "TARGETED_PLANTING", message, target.getPositionX(), target.getPositionY()));
        change.recordEcologyPlacement(
                "TARGETED_PLANTING_V1", EcologyCategory.PLANT, true, null,
                WorldCoordinates.pixelToChunk(target.getPositionX()),
                WorldCoordinates.pixelToChunk(target.getPositionY()),
                "EXACT_FARM_PLOT", 1, EcologyPlacementReason.TARGETED_PLANTING, LocalDateTime.now(clock));
        objects.saveAndFlush(WorldPlacedObject.create(
                change, cropAsset, TerrainType.SOIL, HabitatType.DECORATION_ONLY,
                target.getPositionX(), target.getPositionY()));
        return result(change);
    }
    WorldChangeResult result(WorldChange change) {
        List<Long> ids = objects.findByWorldChangeId(change.getId()).stream().map(WorldPlacedObject::getId).toList();
        return result(change, ids);
    }
    private WorldChangeResult result(WorldChange change, List<Long> ids) {
        EcologyPlacementResult placement = change.getPlacementReason() == null ? null : new EcologyPlacementResult(
                Boolean.TRUE.equals(change.getPlacementApplied()), change.getEcologyCategory(), change.getAssetType(),
                ids.isEmpty() ? null : ids.get(0), change.getPlacementChunkX(), change.getPlacementChunkY(),
                change.getPlacementRegionType(), change.getSpawnZoneTag(), change.getPlacementReason(),
                change.getEcologyProfileKey(), change.getPlacementVersion());
        return new WorldChangeResult(change.getId(),change.getWorldCategory(),change.getAssetType(),change.getMessageKey(),
                change.getDisplayMessage(),ids,!ids.isEmpty(),change.getFocusX(),change.getFocusY(),placement);
    }
    private Mapping mappingFor(RecognizedObject object) {
        if (object == null || object == RecognizedObject.UNKNOWN) return new Mapping(WorldCategory.UNKNOWN,WorldAssetType.MEMORY_SPARK,TerrainType.GRASS,HabitatType.DECORATION_ONLY,"MEMORY","특별한 기억이 마을 어딘가에 작은 변화를 남겼습니다.");
        return switch(object) { case FLOWER, PLANT -> new Mapping(WorldCategory.NATURE,WorldAssetType.FLOWER_CLUSTER,TerrainType.FLOWER_FIELD,HabitatType.DECORATION_ONLY,"FLOWER","이 기억은 마을의 새로운 풍경이 되었습니다."); case TREE -> new Mapping(WorldCategory.NATURE,WorldAssetType.TREE_GROVE,TerrainType.FOREST,HabitatType.DECORATION_ONLY,"TREE","오래 머물 그늘이 마을에 생겼습니다."); case DOG -> new Mapping(WorldCategory.ANIMAL,WorldAssetType.DEFAULT_DOG,TerrainType.GRASS,HabitatType.LAND,"DOG","새로운 강아지 친구가 마을을 찾아왔습니다."); case CAT -> new Mapping(WorldCategory.ANIMAL,WorldAssetType.DEFAULT_CAT,TerrainType.GRASS,HabitatType.LAND,"CAT","새로운 고양이 친구가 마을을 찾아왔습니다."); case BIRD -> new Mapping(WorldCategory.ANIMAL,WorldAssetType.DEFAULT_BIRD,TerrainType.GRASS,HabitatType.AIR,"BIRD","새로운 새 친구가 마을을 찾아왔습니다."); case ANIMAL -> new Mapping(WorldCategory.ANIMAL,WorldAssetType.VISITOR,TerrainType.GRASS,HabitatType.LAND,"LAND_ANIMAL","새로운 손님이 마을을 찾아왔습니다."); case WATER,RIVER,SEA,POND -> new Mapping(WorldCategory.WATER,WorldAssetType.POND,TerrainType.WATER,HabitatType.DECORATION_ONLY,"WATER","물가에 새로운 친구가 찾아왔습니다."); case FOOD,BREAD,FRUIT,VEGETABLE,TOMATO,CARROT,POTATO,WHEAT,COFFEE -> new Mapping(WorldCategory.FOOD,WorldAssetType.BAKERY_DETAIL,TerrainType.GRASS,HabitatType.DECORATION_ONLY,"FOOD","마을에 맛있는 향기가 퍼졌습니다."); case SKY,LANDSCAPE -> new Mapping(WorldCategory.SKY,WorldAssetType.ATMOSPHERE,TerrainType.GRASS,HabitatType.DECORATION_ONLY,"SKY","오늘의 하늘이 마을에 스며들었습니다."); default -> new Mapping(WorldCategory.MEMORY,WorldAssetType.MEMORY_SPARK,TerrainType.GRASS,HabitatType.DECORATION_ONLY,"MEMORY","특별한 기억이 마을 어딘가에 작은 변화를 남겼습니다."); };
    }

    private int[] animalSpawn(Recognition recognition, WorldAssetType asset, World world) {
        Long characterId = recognition.getPhoto().getCharacter().getId();
        Set<String> occupied = objects.findByCharacterIdOrderByIdAsc(characterId).stream()
                .map(object -> pixelKey(object.getPositionX(), object.getPositionY()))
                .collect(Collectors.toCollection(HashSet::new));
        positions.findByCharacterId(characterId).ifPresent(player ->
                occupied.add(pixelKey(
                        WorldCoordinates.tileToPixel(player.getX()),
                        WorldCoordinates.tileToPixel(player.getY()))));
        occupied.add(pixelKey(
                WorldCoordinates.tileToPixel(clamp(11, world.getMinTileX(), world.getMaxTileX())),
                WorldCoordinates.tileToPixel(clamp(8, world.getMinTileY(), world.getMaxTileY()))));
        List<int[]> candidates = new java.util.ArrayList<>();
        List<WorldTerrainTile> persistedTerrain = terrain.findByCharacterIdOrderByYAscXAsc(characterId);
        if (persistedTerrain.isEmpty()) {
            for (int y = world.getMinTileY(); y <= world.getMaxTileY(); y++) {
                for (int x = world.getMinTileX(); x <= world.getMaxTileX(); x++) {
                    if (baseTerrainAt(x, y) == TerrainType.GRASS
                            && !occupied.contains(pixelKey(
                                    WorldCoordinates.tileToPixel(x),
                                    WorldCoordinates.tileToPixel(y)))) {
                    candidates.add(new int[]{x, y});
                }
            }
            }
        } else {
            persistedTerrain.stream()
                    .filter(tile -> world.containsTile(tile.getX(), tile.getY()))
                    .filter(WorldTerrainTile::isWalkable)
                    .filter(tile -> tile.getTerrainType() == TerrainType.GRASS || tile.getTerrainType() == TerrainType.FLOWER_FIELD)
                    .filter(tile -> !occupied.contains(pixelKey(
                            WorldCoordinates.tileToPixel(tile.getX()),
                            WorldCoordinates.tileToPixel(tile.getY()))))
                    .map(tile -> new int[]{tile.getX(), tile.getY()})
                    .forEach(candidates::add);
        }
        if (candidates.isEmpty()) throw new IllegalStateException("동물을 배치할 수 있는 안전한 타일이 없습니다.");
        long seed = 31L * recognition.getId() + recognition.getPhoto().getId() + asset.ordinal();
        return candidates.get(Math.floorMod(Long.hashCode(seed), candidates.size()));
    }

    private void moveAnimalsIfDue(
            Long characterId,
            WorldPlayerPosition player,
            World world,
            List<NpcProjection> runtimeNpcs) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime lastMovedAt = world.getLastAnimalMovementAt();
        if (lastMovedAt == null) {
            world.markAnimalMovement(now);
            return;
        }
        if (Duration.between(lastMovedAt, now).compareTo(ANIMAL_MOVEMENT_INTERVAL) < 0) return;

        List<WorldPlacedObject> all = objects.findByCharacterIdOrderByIdAsc(characterId);
        List<WorldPlacedObject> animals = all.stream().filter(object -> isAnimal(object.getAssetType())).toList();
        if (animals.isEmpty()) {
            world.markAnimalMovement(now);
            return;
        }
        Map<String, WorldTerrainTile> tiles = terrain.findByCharacterIdOrderByYAscXAsc(characterId).stream()
                .collect(Collectors.toMap(tile -> tile.getX() + ":" + tile.getY(), tile -> tile));
        Set<String> occupied = all.stream()
                .map(object -> pixelKey(object.getPositionX(), object.getPositionY()))
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> interactionTargets = all.stream()
                .filter(object -> isTemplateNpc(object.getAssetType())
                        || contextualInteraction(object.getAssetType()) != null)
                .map(object -> pixelKey(object.getPositionX(), object.getPositionY()))
                .collect(Collectors.toCollection(HashSet::new));
        runtimeNpcs.forEach(npc -> {
            String key = pixelKey(npc.pixelX(), npc.pixelY());
            occupied.add(key);
            interactionTargets.add(key);
        });
        occupied.add(pixelKey(
                WorldCoordinates.tileToPixel(player.getX()),
                WorldCoordinates.tileToPixel(player.getY())));
        long bucket = now.toEpochSecond(ZoneOffset.UTC) / ANIMAL_MOVEMENT_INTERVAL.toSeconds();
        int start = Math.floorMod(Long.hashCode(bucket), animals.size());
        int movementCount = Math.min(MAX_ANIMALS_MOVED_PER_TICK, animals.size());
        for (int index = 0; index < movementCount; index++) {
            WorldPlacedObject animal = animals.get((start + index) % animals.size());
            int animalX = WorldCoordinates.pixelToTile(animal.getPositionX());
            int animalY = WorldCoordinates.pixelToTile(animal.getPositionY());
            if (Math.abs(animalX - player.getX()) + Math.abs(animalY - player.getY()) <= 1) continue;
            moveAnimal(animal, bucket, tiles, occupied, interactionTargets, world);
        }
        world.markAnimalMovement(now);
    }

    private void moveAnimal(
            WorldPlacedObject animal,
            long bucket,
            Map<String, WorldTerrainTile> tiles,
            Set<String> occupied,
            Set<String> interactionTargets,
            World world
    ) {
        int currentX = WorldCoordinates.pixelToTile(animal.getPositionX());
        int currentY = WorldCoordinates.pixelToTile(animal.getPositionY());
        String currentKey = pixelKey(animal.getPositionX(), animal.getPositionY());
        int[][] directions = {{0,-1},{1,0},{0,1},{-1,0}};
        int offset = Math.floorMod(Long.hashCode(animal.getId() * 31L + bucket), directions.length);
        occupied.remove(currentKey);
        interactionTargets.remove(currentKey);
        for (int index = 0; index < directions.length; index++) {
            int[] direction = directions[(offset + index) % directions.length];
            int nextX = currentX + direction[0];
            int nextY = currentY + direction[1];
            if (!world.containsTile(nextX, nextY)) continue;
            if (!ecologyPlacement.movementAllowed(animal, world, nextX, nextY)) continue;
            WorldTerrainTile tile = tiles.get(nextX + ":" + nextY);
            String nextKey = pixelKey(
                    WorldCoordinates.tileToPixel(nextX),
                    WorldCoordinates.tileToPixel(nextY));
            if (tile == null || !tile.isWalkable() || tile.getTerrainType() == TerrainType.SOIL
                    || occupied.contains(nextKey)
                    || !hasExclusiveInteractionNeighbor(nextX, nextY, tiles, occupied, interactionTargets)) continue;
            animal.moveTo(
                    tile.getTerrainType(),
                    WorldCoordinates.tileToPixel(nextX),
                    WorldCoordinates.tileToPixel(nextY));
            occupied.add(nextKey);
            interactionTargets.add(nextKey);
            return;
        }
        occupied.add(currentKey);
        interactionTargets.add(currentKey);
    }

    private static boolean hasExclusiveInteractionNeighbor(
            int targetX,
            int targetY,
            Map<String, WorldTerrainTile> tiles,
            Set<String> occupied,
            Set<String> otherInteractionTargets
    ) {
        int[][] directions = {{0,-1},{1,0},{0,1},{-1,0}};
        for (int[] direction : directions) {
            int playerX = targetX + direction[0];
            int playerY = targetY + direction[1];
            WorldTerrainTile playerTile = tiles.get(playerX + ":" + playerY);
            if (playerTile == null || !playerTile.isWalkable()
                    || occupied.contains(pixelKey(
                            WorldCoordinates.tileToPixel(playerX),
                            WorldCoordinates.tileToPixel(playerY)))) continue;
            boolean shared = otherInteractionTargets.stream()
                    .map(key -> key.split(":"))
                    .anyMatch(parts -> Math.abs(WorldCoordinates.pixelToTile(Integer.parseInt(parts[0])) - playerX)
                            + Math.abs(WorldCoordinates.pixelToTile(Integer.parseInt(parts[1])) - playerY) == 1);
            if (!shared) return true;
        }
        return false;
    }

    private static boolean isAnimal(WorldAssetType asset) {
        return asset == WorldAssetType.DEFAULT_DOG || asset == WorldAssetType.DEFAULT_CAT || asset == WorldAssetType.DEFAULT_BIRD;
    }

    private static TerrainType baseTerrainAt(int x, int y) {
        TerrainType type = (x == 11 || y == 7) ? TerrainType.ROAD : TerrainType.GRASS;
        if (x < 2 || x > 21 || y < 2 || y > 13) type = TerrainType.FOREST;
        if (x >= 17 && y >= 11) type = TerrainType.WATER;
        if (WorldHubLayout.isBridge(x, y)) type = TerrainType.BRIDGE;
        if (x == WorldHubLayout.BRIDGE_EXIT_X && y >= 11 && y <= 15) {
            type = y == WorldHubLayout.BRIDGE_Y ? TerrainType.ROAD : TerrainType.GRASS;
        }
        if (WorldHubLayout.isCommunityHouseFootprint(x, y)) type = TerrainType.BUILDING;
        if (x == WorldHubLayout.COMMUNITY_HOUSE_ANCHOR_X
                && y == WorldHubLayout.COMMUNITY_HOUSE_ANCHOR_Y) type = TerrainType.ROAD;
        return type;
    }

    private static HabitatType habitatFor(WorldAssetType asset) {
        if (asset == WorldAssetType.DEFAULT_BIRD) return HabitatType.AIR;
        if (asset == WorldAssetType.DEFAULT_DOG || asset == WorldAssetType.DEFAULT_CAT) return HabitatType.LAND;
        return HabitatType.DECORATION_ONLY;
    }

    private World worldForCharacter(com.projecteden.character.domain.Character character) {
        return worlds.findByCharacterIdForUpdate(character.getId())
                .orElseGet(() -> worlds.save(World.create(character, character.getId())));
    }

    private static MapBoundsResponse bounds(World world) {
        return new MapBoundsResponse(
                world.getMinTileX(),
                world.getMaxTileX(),
                world.getMinTileY(),
                world.getMaxTileY());
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
    private record Mapping(WorldCategory category, WorldAssetType asset, TerrainType terrain, HabitatType habitat, String key, String message) { }
}
