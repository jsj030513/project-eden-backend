package com.projecteden.world;

import static org.assertj.core.api.Assertions.assertThat;

import com.projecteden.character.domain.Character;
import com.projecteden.character.domain.CharacterGender;
import com.projecteden.character.domain.HairStyle;
import com.projecteden.character.domain.CharacterJob;
import com.projecteden.character.domain.Outfit;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.ecology.TerrainType;
import com.projecteden.world.ecology.WorldAssetType;
import com.projecteden.world.ecology.WorldEcologyService;
import com.projecteden.world.ecology.WorldStateResponse;
import com.projecteden.world.ecology.MapBoundsResponse;
import com.projecteden.world.ecology.WorldChange;
import com.projecteden.world.ecology.WorldChangeRepository;
import com.projecteden.world.ecology.WorldCategory;
import com.projecteden.world.ecology.WorldPlacedObject;
import com.projecteden.world.ecology.WorldPlacedObjectRepository;
import com.projecteden.world.ecology.WorldPlayerPositionRepository;
import com.projecteden.world.ecology.WorldTerrainTile;
import com.projecteden.world.ecology.WorldTerrainTileRepository;
import com.projecteden.world.ecology.WorldHubLayout;
import com.projecteden.world.ecology.MoveRequest;
import com.projecteden.world.ecology.HabitatType;
import com.projecteden.ai.domain.Recognition;
import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.ai.repository.RecognitionRepository;
import com.projecteden.photo.domain.Photo;
import com.projecteden.photo.repository.PhotoRepository;
import java.util.UUID;
import com.projecteden.world.repository.WorldRepository;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VillageTemplateIntegrationTests {
    @Autowired private WorldEcologyService ecology;
    @Autowired private UserRepository users;
    @Autowired private CharacterRepository characters;
    @Autowired private WorldRepository worlds;
    @Autowired private PasswordEncoder passwords;
    @Autowired private WorldChangeRepository changes;
    @Autowired private WorldPlacedObjectRepository objects;
    @Autowired private WorldTerrainTileRepository terrain;
    @Autowired private PhotoRepository photos;
    @Autowired private RecognitionRepository recognitions;
    @Autowired private WorldPlayerPositionRepository playerPositions;

    @Test
    void bootstrapsTheExactVersionThreeFarmNpcAndHubFootprints() {
        User user = user("layout"); Character character = character(user);
        World world = worlds.save(World.create(character, 17));
        WorldStateResponse state = ecology.stateForUser(user.getId());

        assertThat(world.getVillageTemplateVersion()).isEqualTo(3);
        assertThat(state.mapBounds()).isEqualTo(new MapBoundsResponse(-8, 31, -8, 23));
        assertThat(state.tileSize()).isEqualTo(48);
        assertThat(state.generationVersion()).isEqualTo(3);
        assertThat(state.terrainTiles()).hasSize(384);
        Map<WorldAssetType, Long> counts = state.placedObjects().stream().collect(Collectors.groupingBy(object -> object.assetType(), Collectors.counting()));
        assertThat(counts).containsEntry(WorldAssetType.PLAZA, 1L).containsEntry(WorldAssetType.FARM_PLOT_EMPTY, 1L)
                .containsEntry(WorldAssetType.COMMUNITY_HOUSE, 1L).containsEntry(WorldAssetType.FARM_CARROT, 8L)
                .containsEntry(WorldAssetType.FARM_FLOWER, 8L).containsEntry(WorldAssetType.FARM_TOMATO, 4L)
                .containsEntry(WorldAssetType.FARM_CABBAGE, 4L).containsEntry(WorldAssetType.DEFAULT_NPC_GUIDE, 1L)
                .containsEntry(WorldAssetType.DEFAULT_NPC_GARDENER, 1L).containsEntry(WorldAssetType.DEFAULT_NPC_MEMORY_KEEPER, 1L)
                .containsEntry(WorldAssetType.DEFAULT_NPC_ANIMAL_CARETAKER, 1L).containsEntry(WorldAssetType.DEFAULT_DOG, 1L)
                .containsEntry(WorldAssetType.DEFAULT_CAT, 1L).containsEntry(WorldAssetType.DEFAULT_BIRD, 2L);
        assertCoordinates(state, WorldAssetType.FARM_CARROT, grid(2, 12, 4, 2));
        assertCoordinates(state, WorldAssetType.FARM_FLOWER, grid(3, 4, 4, 2));
        assertCoordinates(state, WorldAssetType.FARM_TOMATO, grid(7, 10, 2, 2));
        assertCoordinates(state, WorldAssetType.FARM_CABBAGE, grid(9, 10, 2, 2));
        assertThat(state.terrainTiles().stream().filter(tile -> tile.terrainType() == TerrainType.SOIL)).hasSize(32)
                .allSatisfy(tile -> { assertThat(tile.walkable()).isTrue(); assertThat(tile.x()).isBetween(0, 23); assertThat(tile.y()).isBetween(0, 15); });
        assertThat(state.terrainTiles().stream()
                .filter(tile -> tile.y() == WorldHubLayout.BRIDGE_Y
                        && tile.x() >= WorldHubLayout.BRIDGE_MIN_X
                        && tile.x() <= WorldHubLayout.BRIDGE_MAX_X))
                .hasSize(6)
                .allSatisfy(tile -> {
                    assertThat(tile.terrainType()).isEqualTo(TerrainType.BRIDGE);
                    assertThat(tile.walkable()).isTrue();
                });
        assertThat(state.terrainTiles().stream()
                .filter(tile -> tile.x() >= WorldHubLayout.BRIDGE_MIN_X
                        && tile.x() <= WorldHubLayout.BRIDGE_MAX_X
                        && (tile.y() == WorldHubLayout.BRIDGE_Y - 1
                            || tile.y() == WorldHubLayout.BRIDGE_Y + 1)))
                .allSatisfy(tile -> {
                    assertThat(tile.terrainType()).isEqualTo(TerrainType.WATER);
                    assertThat(tile.walkable()).isFalse();
                });
        assertThat(state.placedObjects().stream()
                .filter(object -> object.assetType() == WorldAssetType.COMMUNITY_HOUSE))
                .singleElement()
                .satisfies(house -> {
                    assertThat(house.x() / 48).isEqualTo(WorldHubLayout.COMMUNITY_HOUSE_ANCHOR_X);
                    assertThat(house.y() / 48).isEqualTo(WorldHubLayout.COMMUNITY_HOUSE_ANCHOR_Y);
                    assertThat(house.terrainType()).isEqualTo(TerrainType.ROAD);
                });
        assertThat(state.placedObjects()).allSatisfy(object -> { assertThat(object.x() / 48).isBetween(0, 23); assertThat(object.y() / 48).isBetween(0, 15); });
    }

    @Test
    void secondBootstrapDoesNotAddAnyTemplateRows() {
        User user = user("idempotent"); Character character = character(user); worlds.save(World.create(character, 19));
        WorldStateResponse first = ecology.stateForUser(user.getId());
        WorldStateResponse second = ecology.stateForUser(user.getId());
        assertThat(second.placedObjects()).hasSameSizeAs(first.placedObjects());
        assertThat(second.terrainTiles().stream().filter(tile -> tile.terrainType() == TerrainType.SOIL).count()).isEqualTo(32);
        assertThat(second.placedObjects().stream().map(object -> object.worldChangeId()).collect(Collectors.toSet())).hasSize(second.changes().size());
    }

    @Test
    void bridgeSupportsEntryCenterExitPersistenceAndBlocksAdjacentWater() {
        User user = user("bridge-movement");
        Character character = character(user);
        worlds.save(World.create(character, 37));
        ecology.stateForUser(user.getId());
        var player = playerPositions.findByCharacterId(character.getId()).orElseThrow();
        player.moveTo(WorldHubLayout.BRIDGE_ENTRY_X, WorldHubLayout.BRIDGE_Y);

        for (int x = WorldHubLayout.BRIDGE_MIN_X; x <= WorldHubLayout.BRIDGE_MAX_X; x++) {
            var moved = ecology.move(user.getId(), new MoveRequest(x, WorldHubLayout.BRIDGE_Y));
            assertThat(moved.accepted()).isTrue();
            assertThat(moved.terrainType()).isEqualTo(TerrainType.BRIDGE);
        }
        var exited = ecology.move(user.getId(), new MoveRequest(
                WorldHubLayout.BRIDGE_EXIT_X, WorldHubLayout.BRIDGE_Y));
        assertThat(exited.accepted()).isTrue();
        assertThat(ecology.stateForUser(user.getId()).playerPosition())
                .extracting(position -> position.x(), position -> position.y())
                .containsExactly(WorldHubLayout.BRIDGE_EXIT_X, WorldHubLayout.BRIDGE_Y);

        player.moveTo(18, WorldHubLayout.BRIDGE_Y);
        var blocked = ecology.move(user.getId(), new MoveRequest(18, WorldHubLayout.BRIDGE_Y - 1));
        assertThat(blocked.accepted()).isFalse();
        assertThat(blocked.reason()).isEqualTo("TERRAIN_BLOCKED");
        assertThat(blocked.terrainType()).isEqualTo(TerrainType.WATER);
        assertThat(player.getX()).isEqualTo(18);
        assertThat(player.getY()).isEqualTo(WorldHubLayout.BRIDGE_Y);
    }

    @Test
    void communityHouseEntranceIsWalkableWhileItsBodyRemainsBlocked() {
        User user = user("house-collision");
        Character character = character(user);
        worlds.save(World.create(character, 41));
        ecology.stateForUser(user.getId());
        var player = playerPositions.findByCharacterId(character.getId()).orElseThrow();
        player.moveTo(WorldHubLayout.COMMUNITY_HOUSE_APPROACH_X, WorldHubLayout.COMMUNITY_HOUSE_APPROACH_Y);

        var entrance = ecology.move(user.getId(), new MoveRequest(
                WorldHubLayout.COMMUNITY_HOUSE_ANCHOR_X,
                WorldHubLayout.COMMUNITY_HOUSE_ANCHOR_Y));
        assertThat(entrance.accepted()).isTrue();
        assertThat(entrance.terrainType()).isEqualTo(TerrainType.ROAD);

        var body = ecology.move(user.getId(), new MoveRequest(
                WorldHubLayout.COMMUNITY_HOUSE_ANCHOR_X,
                WorldHubLayout.COMMUNITY_HOUSE_MAX_Y));
        assertThat(body.accepted()).isFalse();
        assertThat(body.reason()).isEqualTo("TERRAIN_BLOCKED");
        assertThat(body.terrainType()).isEqualTo(TerrainType.BUILDING);
        assertThat(player.getX()).isEqualTo(WorldHubLayout.COMMUNITY_HOUSE_ANCHOR_X);
        assertThat(player.getY()).isEqualTo(WorldHubLayout.COMMUNITY_HOUSE_ANCHOR_Y);
    }

    @Test
    void persistsAndReadsATemplateChangeWithoutRecognition() {
        User user = user("nullable"); Character character = character(user);
        WorldChange template = changes.save(WorldChange.template(character, WorldCategory.MEMORY,
                WorldAssetType.MEMORY_SPARK, "TEMPLATE_NULL_RECOGNITION", "기본 풍경", 48, 48));

        assertThat(template.getId()).isNotNull();
        assertThat(changes.findByCharacterIdAndMessageKey(character.getId(), "TEMPLATE_NULL_RECOGNITION"))
                .isPresent().get().extracting(WorldChange::getId).isEqualTo(template.getId());
    }

    @Test
    void preservesExistingPhotoRecognitionChangeObjectAndTerrainWhenAddingTemplate() {
        User user = user("existing"); Character character = character(user); World world = worlds.save(World.create(character, 23));
        String stored = UUID.randomUUID() + ".jpg";
        Photo photo = photos.save(Photo.create(character, null, "existing.jpg", stored, "image/jpeg", 10, "/uploads/photos/" + stored));
        Recognition recognition = recognitions.save(Recognition.create(photo, RecognizedObject.FLOWER, 91, true));
        WorldChange existingChange = changes.save(WorldChange.create(character, recognition, WorldCategory.NATURE,
                WorldAssetType.FLOWER_CLUSTER, "EXISTING_MEMORY", "기존 기억", 48, 48));
        WorldPlacedObject existingObject = objects.save(WorldPlacedObject.create(existingChange, WorldAssetType.FLOWER_CLUSTER,
                TerrainType.GRASS, HabitatType.DECORATION_ONLY, 48, 48));
        WorldTerrainTile existingTerrain = terrain.save(WorldTerrainTile.create(character, 0, 0, TerrainType.GRASS));

        ecology.stateForUser(user.getId());

        assertThat(world.getVillageTemplateVersion()).isEqualTo(3);
        assertThat(recognitions.findById(recognition.getId())).isPresent();
        assertThat(changes.findByRecognitionId(recognition.getId())).isPresent().get().extracting(WorldChange::getId).isEqualTo(existingChange.getId());
        assertThat(objects.findById(existingObject.getId())).isPresent();
        assertThat(objects.findByWorldChangeId(existingChange.getId())).extracting(WorldPlacedObject::getId).contains(existingObject.getId());
        assertThat(terrain.findByCharacterIdAndXAndY(character.getId(), existingTerrain.getX(), existingTerrain.getY())).isPresent();
    }

    @Test
    void reconcilesExistingHouseAndBridgeWithoutReplacingPersistentIds() {
        User user = user("hub-reconcile");
        Character character = character(user);
        World world = worlds.save(World.create(character, 31));
        world.applyVillageTemplateVersion(2);
        for (int y = 0; y <= 15; y++) {
            for (int x = 0; x <= 23; x++) {
                terrain.save(WorldTerrainTile.create(character, x, y,
                        x >= 17 && y >= 11 ? TerrainType.WATER : TerrainType.GRASS));
            }
        }
        WorldChange houseChange = changes.save(WorldChange.template(
                character, WorldCategory.MEMORY, WorldAssetType.COMMUNITY_HOUSE,
                "TEMPLATE_HOUSE", "기본 마을 풍경", 7 * 48, 4 * 48));
        WorldPlacedObject house = objects.save(WorldPlacedObject.create(
                houseChange, WorldAssetType.COMMUNITY_HOUSE, TerrainType.GRASS,
                HabitatType.DECORATION_ONLY, 7 * 48, 4 * 48));

        ecology.stateForUser(user.getId());
        ecology.stateForUser(user.getId());

        assertThat(world.getVillageTemplateVersion()).isEqualTo(3);
        assertThat(changes.findById(houseChange.getId())).isPresent().get().satisfies(change -> {
            assertThat(change.getFocusX()).isEqualTo(14 * 48);
            assertThat(change.getFocusY()).isEqualTo(6 * 48);
        });
        assertThat(objects.findById(house.getId())).isPresent().get().satisfies(persisted -> {
            assertThat(persisted.getPositionX()).isEqualTo(14 * 48);
            assertThat(persisted.getPositionY()).isEqualTo(6 * 48);
            assertThat(persisted.getTerrain()).isEqualTo(TerrainType.ROAD);
        });
        assertThat(terrain.findByCharacterIdAndXAndY(character.getId(), 17, 13))
                .isPresent().get().satisfies(tile -> {
                    assertThat(tile.getTerrainType()).isEqualTo(TerrainType.BRIDGE);
                    assertThat(tile.isWalkable()).isTrue();
                });
        assertThat(changes.findByCharacterIdAndMessageKey(character.getId(), "TEMPLATE_HOUSE"))
                .isPresent().get().extracting(WorldChange::getId).isEqualTo(houseChange.getId());
        assertThat(objects.findByWorldChangeId(houseChange.getId()))
                .extracting(WorldPlacedObject::getId).containsExactly(house.getId());
    }

    @Test
    void createsOnePersistentTypedAnimalPerDistinctPhotoAndKeepsRecognitionIdempotent() {
        User user = user("memory-animals");
        Character character = character(user);
        worlds.save(World.create(character, 29));
        ecology.stateForUser(user.getId());

        Recognition firstDog = recognition(character, RecognizedObject.DOG, "dog-a.jpg");
        Recognition secondDog = recognition(character, RecognizedObject.DOG, "dog-b.jpg");
        Recognition cat = recognition(character, RecognizedObject.CAT, "cat.jpg");
        Recognition bird = recognition(character, RecognizedObject.BIRD, "bird.jpg");

        var first = ecology.createFor(firstDog);
        var retry = ecology.createFor(firstDog);
        var second = ecology.createFor(secondDog);
        var catResult = ecology.createFor(cat);
        var birdResult = ecology.createFor(bird);
        WorldStateResponse state = ecology.stateForUser(user.getId());

        assertThat(retry.worldChangeId()).isEqualTo(first.worldChangeId());
        assertThat(retry.spawnedObjectIds()).containsExactlyElementsOf(first.spawnedObjectIds());
        assertThat(second.spawnedObjectIds().get(0)).isNotEqualTo(first.spawnedObjectIds().get(0));
        assertThat(catResult.assetType()).isEqualTo(WorldAssetType.DEFAULT_CAT);
        assertThat(birdResult.assetType()).isEqualTo(WorldAssetType.DEFAULT_BIRD);
        assertThat(state.placedObjects().stream().filter(object -> object.assetType() == WorldAssetType.DEFAULT_DOG)).hasSize(3);
        assertThat(state.placedObjects().stream().filter(object -> object.assetType() == WorldAssetType.DEFAULT_CAT)).hasSize(2);
        assertThat(state.placedObjects().stream().filter(object -> object.assetType() == WorldAssetType.DEFAULT_BIRD)).hasSize(3);
        assertThat(state.placedObjects().stream().filter(object -> first.spawnedObjectIds().contains(object.id())
                        || second.spawnedObjectIds().contains(object.id()) || catResult.spawnedObjectIds().contains(object.id())
                        || birdResult.spawnedObjectIds().contains(object.id())))
                .allSatisfy(object -> {
                    assertThat(object.x() % 48).isZero();
                    assertThat(object.y() % 48).isZero();
                    assertThat(object.x() / 48).isBetween(0, 23);
                    assertThat(object.y() / 48).isBetween(0, 15);
                    assertThat(object.terrainType()).isIn(TerrainType.GRASS, TerrainType.FLOWER_FIELD);
                });
    }

    @Test
    void movesAnimalsInOnePersistentWorldBatchWithoutChangingIdsOrMovingTwiceImmediately() {
        User user = user("animal-movement");
        Character character = character(user);
        World world = worlds.save(World.create(character, 31));
        WorldStateResponse initial = ecology.stateForUser(user.getId());
        Map<Long, String> before = animalCoordinates(initial);

        world.markAnimalMovement(LocalDateTime.now().minusMinutes(1));
        WorldStateResponse moved = ecology.stateForUser(user.getId());
        WorldStateResponse immediateReload = ecology.stateForUser(user.getId());
        Map<Long, String> after = animalCoordinates(moved);

        assertThat(after.keySet()).isEqualTo(before.keySet());
        assertThat(after).isNotEqualTo(before);
        assertThat(animalCoordinates(immediateReload)).isEqualTo(after);
        assertThat(moved.placedObjects().stream().filter(object -> after.containsKey(object.id())))
                .allSatisfy(object -> {
                    assertThat(object.x() % 48).isZero();
                    assertThat(object.y() % 48).isZero();
                    var tile = moved.terrainTiles().stream()
                            .filter(candidate -> candidate.x() == object.x() / 48 && candidate.y() == object.y() / 48)
                            .findFirst().orElseThrow();
                    assertThat(tile.walkable()).isTrue();
                });

        var animal = moved.placedObjects().stream()
                .filter(object -> after.containsKey(object.id()))
                .findFirst().orElseThrow();
        int animalX = animal.x() / 48;
        int animalY = animal.y() / 48;
        var adjacent = moved.terrainTiles().stream()
                .filter(tile -> tile.walkable() && Math.abs(tile.x() - animalX) + Math.abs(tile.y() - animalY) == 1)
                .findFirst().orElseThrow();
        playerPositions.findByCharacterId(character.getId()).orElseThrow().moveTo(adjacent.x(), adjacent.y());
        WorldStateResponse interactionState = ecology.stateForUser(user.getId());
        assertThat(interactionState.availableInteractions())
                .anySatisfy(interaction -> {
                    assertThat(interaction.targetId()).isEqualTo(animal.id());
                    assertThat(interaction.x()).isEqualTo(animalX);
                    assertThat(interaction.y()).isEqualTo(animalY);
                });
    }

    private void assertCoordinates(WorldStateResponse state, WorldAssetType type, Set<String> expected) {
        assertThat(state.placedObjects().stream().filter(object -> object.assetType() == type).map(object -> (object.x() / 48) + "," + (object.y() / 48)).collect(Collectors.toSet())).isEqualTo(expected);
    }
    private static Set<String> grid(int startX, int startY, int width, int height) { return java.util.stream.IntStream.range(0, height).boxed().flatMap(y -> java.util.stream.IntStream.range(0, width).mapToObj(x -> (startX + x) + "," + (startY + y))).collect(Collectors.toSet()); }
    private static Map<Long, String> animalCoordinates(WorldStateResponse state) {
        return state.placedObjects().stream()
                .filter(object -> object.assetType() == WorldAssetType.DEFAULT_DOG
                        || object.assetType() == WorldAssetType.DEFAULT_CAT
                        || object.assetType() == WorldAssetType.DEFAULT_BIRD)
                .collect(Collectors.toMap(object -> object.id(), object -> object.x() + "," + object.y()));
    }
    private User user(String suffix) { return users.save(new User("template-" + suffix + "@example.com", passwords.encode("password123"), "template-" + suffix)); }
    private Character character(User user) { return characters.save(Character.create(user, "에덴", CharacterGender.NONE, HairStyle.PIXEL_CUT, "brown", Outfit.ROBE, CharacterJob.WIZARD)); }
    private Recognition recognition(Character character, RecognizedObject object, String filename) {
        String stored = UUID.randomUUID() + ".jpg";
        Photo photo = photos.save(Photo.create(character, null, filename, stored, "image/jpeg", 10, "/uploads/photos/" + stored));
        return recognitions.save(Recognition.create(photo, object, 95, true));
    }
}
