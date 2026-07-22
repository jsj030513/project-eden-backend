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
import com.projecteden.world.ecology.WorldChange;
import com.projecteden.world.ecology.WorldChangeRepository;
import com.projecteden.world.ecology.WorldCategory;
import com.projecteden.world.ecology.WorldPlacedObject;
import com.projecteden.world.ecology.WorldPlacedObjectRepository;
import com.projecteden.world.ecology.WorldTerrainTile;
import com.projecteden.world.ecology.WorldTerrainTileRepository;
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

    @Test
    void bootstrapsTheExactVersionTwoFarmAndNpcLayout() {
        User user = user("layout"); Character character = character(user);
        World world = worlds.save(World.create(character, 17));
        WorldStateResponse state = ecology.stateForUser(user.getId());

        assertThat(world.getVillageTemplateVersion()).isEqualTo(2);
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

        assertThat(world.getVillageTemplateVersion()).isEqualTo(2);
        assertThat(recognitions.findById(recognition.getId())).isPresent();
        assertThat(changes.findByRecognitionId(recognition.getId())).isPresent().get().extracting(WorldChange::getId).isEqualTo(existingChange.getId());
        assertThat(objects.findById(existingObject.getId())).isPresent();
        assertThat(objects.findByWorldChangeId(existingChange.getId())).extracting(WorldPlacedObject::getId).contains(existingObject.getId());
        assertThat(terrain.findByCharacterIdAndXAndY(character.getId(), existingTerrain.getX(), existingTerrain.getY())).isPresent();
    }

    private void assertCoordinates(WorldStateResponse state, WorldAssetType type, Set<String> expected) {
        assertThat(state.placedObjects().stream().filter(object -> object.assetType() == type).map(object -> (object.x() / 48) + "," + (object.y() / 48)).collect(Collectors.toSet())).isEqualTo(expected);
    }
    private static Set<String> grid(int startX, int startY, int width, int height) { return java.util.stream.IntStream.range(0, height).boxed().flatMap(y -> java.util.stream.IntStream.range(0, width).mapToObj(x -> (startX + x) + "," + (startY + y))).collect(Collectors.toSet()); }
    private User user(String suffix) { return users.save(new User("template-" + suffix + "@example.com", passwords.encode("password123"), "template-" + suffix)); }
    private Character character(User user) { return characters.save(Character.create(user, "에덴", CharacterGender.NONE, HairStyle.PIXEL_CUT, "brown", Outfit.ROBE, CharacterJob.WIZARD)); }
}
