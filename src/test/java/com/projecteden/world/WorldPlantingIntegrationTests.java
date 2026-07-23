package com.projecteden.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecteden.ai.domain.Recognition;
import com.projecteden.ai.repository.RecognitionRepository;
import com.projecteden.auth.jwt.JwtTokenProvider;
import com.projecteden.character.domain.Character;
import com.projecteden.character.domain.CharacterGender;
import com.projecteden.character.domain.CharacterJob;
import com.projecteden.character.domain.HairStyle;
import com.projecteden.character.domain.Outfit;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.photo.domain.Photo;
import com.projecteden.photo.repository.PhotoRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.ecology.HabitatType;
import com.projecteden.world.ecology.PlantMemoryRequest;
import com.projecteden.world.ecology.PlantMemoryResponse;
import com.projecteden.world.ecology.TerrainType;
import com.projecteden.world.ecology.TileInteractionCategory;
import com.projecteden.world.ecology.WorldAssetType;
import com.projecteden.world.ecology.WorldCategory;
import com.projecteden.world.ecology.WorldChange;
import com.projecteden.world.ecology.WorldChangeRepository;
import com.projecteden.world.ecology.WorldEcologyService;
import com.projecteden.world.ecology.WorldPlacedObject;
import com.projecteden.world.ecology.WorldPlacedObjectRepository;
import com.projecteden.world.ecology.WorldPlayerPosition;
import com.projecteden.world.ecology.WorldPlayerPositionRepository;
import com.projecteden.world.repository.WorldRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WorldPlantingIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository users;
    @Autowired private CharacterRepository characters;
    @Autowired private WorldRepository worlds;
    @Autowired private PhotoRepository photos;
    @Autowired private RecognitionRepository recognitions;
    @Autowired private WorldChangeRepository changes;
    @Autowired private WorldPlacedObjectRepository objects;
    @Autowired private WorldPlayerPositionRepository positions;
    @Autowired private WorldEcologyService ecology;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider tokens;

    @ParameterizedTest
    @MethodSource("plantableMappings")
    void safelyMapsPlantableRecognitionToTheTargetPlot(
            String originalFileName,
            String recognizedObject,
            WorldAssetType expectedCrop) throws Exception {
        Fixture fixture = fixture("mapping-" + expectedCrop.name().toLowerCase());
        Photo photo = photo(fixture.character(), originalFileName);

        PlantMemoryResponse response = plant(fixture, photo, fixture.target(), 3, 9)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoId").value(photo.getId()))
                .andExpect(jsonPath("$.targetId").value(fixture.target().getId()))
                .andExpect(jsonPath("$.targetX").value(3))
                .andExpect(jsonPath("$.targetY").value(9))
                .andExpect(jsonPath("$.plantingApplied").value(true))
                .andExpect(jsonPath("$.cropAssetType").value(expectedCrop.name()))
                .andExpect(jsonPath("$.recognition.recognizedObject").value(recognizedObject))
                .andExpect(jsonPath("$.worldChange.assetType").value(expectedCrop.name()))
                .andReturn().getResponse().getContentAsString().transform(this::readResponse);

        WorldChange change = changes.findByTargetObjectId(fixture.target().getId()).orElseThrow();
        assertThat(change.getRecognition().getId()).isEqualTo(response.recognition().id());
        assertThat(change.getTargetObject().getId()).isEqualTo(fixture.target().getId());
        assertThat(objects.findByWorldChangeId(change.getId()))
                .singleElement()
                .satisfies(crop -> {
                    assertThat(crop.getAssetType()).isEqualTo(expectedCrop);
                    assertThat(crop.getTerrain()).isEqualTo(TerrainType.SOIL);
                    assertThat(crop.getPositionX()).isEqualTo(3 * 48);
                    assertThat(crop.getPositionY()).isEqualTo(9 * 48);
                });
    }

    @Test
    void preservesANonPlantableRecognitionWithoutChangingTheTarget() throws Exception {
        Fixture fixture = fixture("non-plantable");
        Photo photo = photo(fixture.character(), "cat-memory.jpg");
        long changesBefore = changes.count();
        long objectsBefore = objects.count();

        PlantMemoryResponse first = plant(fixture, photo, fixture.target(), 3, 9)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plantingApplied").value(false))
                .andExpect(jsonPath("$.cropAssetType").value(nullValue()))
                .andExpect(jsonPath("$.recognition.recognizedObject").value("CAT"))
                .andExpect(jsonPath("$.recognition.worldChange").value(nullValue()))
                .andExpect(jsonPath("$.worldChange").value(nullValue()))
                .andReturn().getResponse().getContentAsString().transform(this::readResponse);

        PlantMemoryResponse retry = plant(fixture, photo, fixture.target(), 3, 9)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(this::readResponse);

        assertThat(retry.recognition().id()).isEqualTo(first.recognition().id());
        assertThat(changes.count()).isEqualTo(changesBefore);
        assertThat(objects.count()).isEqualTo(objectsBefore);
        assertThat(changes.findByTargetObjectId(fixture.target().getId())).isEmpty();
        assertThat(recognitions.findByPhotoId(photo.getId())).isPresent().get()
                .extracting(Recognition::getPlantingTargetObject)
                .extracting(WorldPlacedObject::getId)
                .isEqualTo(fixture.target().getId());
        assertThat(ecology.stateForUser(fixture.user().getId()).placedObjects())
                .anyMatch(object -> object.id().equals(fixture.target().getId())
                        && object.assetType() == WorldAssetType.FARM_PLOT_EMPTY);
    }

    @Test
    void rejectsAnotherUsersPhotoAndTarget() throws Exception {
        Fixture owner = fixture("owner");
        Fixture intruder = fixture("intruder");
        Photo intruderPhoto = photo(intruder.character(), "flower-intruder.jpg");
        Photo ownerPhoto = photo(owner.character(), "flower-owner.jpg");

        plant(owner, intruderPhoto, owner.target(), 3, 9)
                .andExpect(status().isForbidden());
        plant(owner, ownerPhoto, intruder.target(), 3, 9)
                .andExpect(status().isForbidden());

        assertThat(recognitions.findByPhotoId(intruderPhoto.getId())).isEmpty();
        assertThat(recognitions.findByPhotoId(ownerPhoto.getId())).isEmpty();
    }

    @Test
    void rejectsNonEmptyTargetAndStaleCoordinates() throws Exception {
        Fixture fixture = fixture("target-validation");
        Photo nonEmptyPhoto = photo(fixture.character(), "flower-non-empty.jpg");
        WorldPlacedObject crop = ecology.stateForUser(fixture.user().getId()).placedObjects().stream()
                .filter(object -> object.assetType() == WorldAssetType.FARM_CARROT)
                .findFirst()
                .flatMap(object -> objects.findById(object.id()))
                .orElseThrow();

        plant(fixture, nonEmptyPhoto, crop, crop.getPositionX() / 48, crop.getPositionY() / 48)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("TARGET_CHANGED"));

        Photo stalePhoto = photo(fixture.character(), "flower-stale.jpg");
        plant(fixture, stalePhoto, fixture.target(), 4, 9)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("TARGET_CHANGED"));
        assertThat(recognitions.findByPhotoId(stalePhoto.getId())).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("outOfRangePositions")
    void rejectsSameTileDiagonalAndDistantPositions(int playerX, int playerY) throws Exception {
        Fixture fixture = fixture("range-" + playerX + "-" + playerY);
        move(fixture.character(), playerX, playerY);
        Photo photo = photo(fixture.character(), "flower-range.jpg");

        plant(fixture, photo, fixture.target(), 3, 9)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("TARGET_OUT_OF_RANGE"));
        assertThat(recognitions.findByPhotoId(photo.getId())).isEmpty();
    }

    @Test
    void replacesTheEmptyPlotOnlyInProjectionAndIsIdempotent() throws Exception {
        Fixture fixture = fixture("projection");
        Photo photo = photo(fixture.character(), "flower-projection.jpg");
        Long targetId = fixture.target().getId();
        long changesBefore = changes.count();
        long objectsBefore = objects.count();

        PlantMemoryResponse first = plant(fixture, photo, fixture.target(), 3, 9)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(this::readResponse);
        PlantMemoryResponse second = plant(fixture, photo, fixture.target(), 3, 9)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(this::readResponse);

        assertThat(second).isEqualTo(first);
        assertThat(changes.count()).isEqualTo(changesBefore + 1);
        assertThat(objects.count()).isEqualTo(objectsBefore + 1);
        assertThat(objects.findById(targetId)).isPresent()
                .get().extracting(WorldPlacedObject::getAssetType)
                .isEqualTo(WorldAssetType.FARM_PLOT_EMPTY);

        var state = ecology.stateForUser(fixture.user().getId());
        assertThat(state.placedObjects())
                .noneMatch(object -> object.id().equals(targetId))
                .filteredOn(object -> object.x() == 3 * 48 && object.y() == 9 * 48)
                .singleElement()
                .extracting(object -> object.assetType())
                .isEqualTo(WorldAssetType.FARM_FLOWER);
        assertThat(state.availableInteractions())
                .filteredOn(interaction -> interaction.x() == 3 && interaction.y() == 9)
                .singleElement()
                .satisfies(interaction -> {
                    assertThat(interaction.targetAssetType()).isEqualTo(WorldAssetType.FARM_FLOWER);
                    assertThat(interaction.category()).isEqualTo(TileInteractionCategory.CROP);
                });
    }

    @Test
    void rejectsSamePhotoForAnotherTarget() throws Exception {
        Fixture fixture = fixture("photo-conflict");
        WorldPlacedObject secondTarget = emptyTarget(fixture.character(), "SECOND_EMPTY", 4, 8);
        Photo photo = photo(fixture.character(), "flower-photo-conflict.jpg");

        plant(fixture, photo, fixture.target(), 3, 9).andExpect(status().isOk());
        plant(fixture, photo, secondTarget, 4, 8)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("PHOTO_ALREADY_EXPRESSED"));
        assertThat(changes.findByTargetObjectId(secondTarget.getId())).isEmpty();
    }

    @Test
    void rejectsAnotherPhotoForAnAlreadyPlantedTarget() throws Exception {
        Fixture fixture = fixture("target-conflict");
        Photo winner = photo(fixture.character(), "flower-winner.jpg");
        Photo loser = photo(fixture.character(), "carrot-loser.jpg");

        plant(fixture, winner, fixture.target(), 3, 9).andExpect(status().isOk());
        plant(fixture, loser, fixture.target(), 3, 9)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("TARGET_ALREADY_PLANTED"));
        assertThat(recognitions.findByPhotoId(loser.getId())).isEmpty();
        assertThat(changes.findByTargetObjectId(fixture.target().getId())).isPresent();
    }

    @Test
    void treatsANonPlantablePhotoAsTerminalForItsOriginalTarget() throws Exception {
        Fixture fixture = fixture("non-plantable-target-conflict");
        WorldPlacedObject secondTarget = emptyTarget(fixture.character(), "SECOND_NON_PLANTABLE_EMPTY", 4, 8);
        Photo photo = photo(fixture.character(), "cat-non-plantable.jpg");

        plant(fixture, photo, fixture.target(), 3, 9)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plantingApplied").value(false));
        plant(fixture, photo, secondTarget, 4, 8)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("PHOTO_ALREADY_EXPRESSED"));

        assertThat(changes.findByTargetObjectId(fixture.target().getId())).isEmpty();
        assertThat(changes.findByTargetObjectId(secondTarget.getId())).isEmpty();
        assertThat(objects.findById(fixture.target().getId())).isPresent();
        assertThat(objects.findById(secondTarget.getId())).isPresent();
    }

    @Test
    void rejectsAPlantingAttemptAfterGeneralRecognition() throws Exception {
        Fixture fixture = fixture("general-first");
        Photo photo = photo(fixture.character(), "flower-general-first.jpg");

        mockMvc.perform(post("/api/photos/{photoId}/recognize", photo.getId())
                        .header("Authorization", bearer(fixture.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.worldChange").isNotEmpty());

        plant(fixture, photo, fixture.target(), 3, 9)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("PHOTO_ALREADY_EXPRESSED"));
        WorldChange generalChange = changes.findByRecognitionId(
                recognitions.findByPhotoId(photo.getId()).orElseThrow().getId()).orElseThrow();
        assertThat(generalChange.getTargetObject()).isNull();
    }

    @Test
    void repeatedBootstrapPreservesTheTargetedChangeAndCrop() throws Exception {
        Fixture fixture = fixture("bootstrap");
        Photo photo = photo(fixture.character(), "carrot-bootstrap.jpg");
        PlantMemoryResponse response = plant(fixture, photo, fixture.target(), 3, 9)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(this::readResponse);
        long changesAfterPlanting = changes.count();
        long objectsAfterPlanting = objects.count();

        var first = ecology.stateForUser(fixture.user().getId());
        var second = ecology.stateForUser(fixture.user().getId());

        assertThat(changes.count()).isEqualTo(changesAfterPlanting);
        assertThat(objects.count()).isEqualTo(objectsAfterPlanting);
        assertThat(second.placedObjects()).isEqualTo(first.placedObjects());
        assertThat(second.placedObjects()).anyMatch(object -> object.id().equals(response.worldChange().spawnedObjectIds().getFirst()));
        assertThat(second.placedObjects()).noneMatch(object -> object.id().equals(fixture.target().getId()));
    }

    @Test
    void preservesAnUnrelatedExistingMemoryChainTemplateAndPlayerPosition() throws Exception {
        Fixture fixture = fixture("existing-world-preservation");
        Photo existingPhoto = photo(fixture.character(), "book-existing-memory.jpg");

        mockMvc.perform(post("/api/photos/{photoId}/recognize", existingPhoto.getId())
                        .header("Authorization", bearer(fixture.token())))
                .andExpect(status().isOk());

        Recognition existingRecognition = recognitions.findByPhotoId(existingPhoto.getId()).orElseThrow();
        WorldChange existingChange = changes.findByRecognitionId(existingRecognition.getId()).orElseThrow();
        WorldPlacedObject existingObject = objects.findByWorldChangeId(existingChange.getId()).getFirst();
        Set<Long> existingTemplateObjectIds = ecology.stateForUser(fixture.user().getId()).placedObjects().stream()
                .filter(object -> object.worldChangeId() != null
                        && !object.worldChangeId().equals(existingChange.getId()))
                .map(object -> object.id())
                .collect(java.util.stream.Collectors.toSet());
        WorldPlayerPosition existingPosition = positions.findByCharacterId(fixture.character().getId()).orElseThrow();
        int playerX = existingPosition.getX();
        int playerY = existingPosition.getY();

        Photo plantingPhoto = photo(fixture.character(), "flower-preservation.jpg");
        plant(fixture, plantingPhoto, fixture.target(), 3, 9)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plantingApplied").value(true));

        assertThat(photos.findById(existingPhoto.getId())).isPresent();
        assertThat(recognitions.findById(existingRecognition.getId())).isPresent();
        assertThat(changes.findById(existingChange.getId())).isPresent().get()
                .satisfies(change -> assertThat(change.getTargetObject()).isNull());
        assertThat(objects.findById(existingObject.getId())).isPresent();
        assertThat(existingTemplateObjectIds).allMatch(objects::existsById);
        assertThat(positions.findByCharacterId(fixture.character().getId())).isPresent().get()
                .satisfies(position -> {
                    assertThat(position.getX()).isEqualTo(playerX);
                    assertThat(position.getY()).isEqualTo(playerY);
                });
    }

    @Test
    void validatesMissingFieldsAndMissingResourcesThroughHttp() throws Exception {
        Fixture fixture = fixture("http-errors");

        mockMvc.perform(post("/api/worlds/me/plant-memory")
                        .header("Authorization", bearer(fixture.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isString());

        mockMvc.perform(post("/api/worlds/me/plant-memory")
                        .header("Authorization", bearer(fixture.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlantMemoryRequest(999999L, fixture.target().getId(), 3, 9))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("사진을 찾을 수 없습니다."));
    }

    private org.springframework.test.web.servlet.ResultActions plant(
            Fixture fixture,
            Photo photo,
            WorldPlacedObject target,
            int expectedX,
            int expectedY) throws Exception {
        return mockMvc.perform(post("/api/worlds/me/plant-memory")
                .header("Authorization", bearer(fixture.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new PlantMemoryRequest(photo.getId(), target.getId(), expectedX, expectedY))));
    }

    private Fixture fixture(String suffix) {
        String unique = suffix + "-" + UUID.randomUUID().toString().substring(0, 8);
        User user = users.save(new User(
                "planting-" + unique + "@example.com",
                passwordEncoder.encode("password123"),
                "planting-" + unique));
        Character character = characters.save(Character.create(
                user, "에덴", CharacterGender.NONE, HairStyle.PIXEL_CUT,
                "brown", Outfit.ROBE, CharacterJob.WIZARD));
        worlds.save(World.create(character, unique.hashCode()));
        ecology.stateForUser(user.getId());
        move(character, 3, 8);
        WorldPlacedObject target = ecology.stateForUser(user.getId()).placedObjects().stream()
                .filter(object -> object.assetType() == WorldAssetType.FARM_PLOT_EMPTY)
                .findFirst()
                .flatMap(object -> objects.findById(object.id()))
                .orElseThrow();
        return new Fixture(user, character, target, tokens.generateAccessToken(user));
    }

    private Photo photo(Character character, String originalFileName) {
        String stored = UUID.randomUUID() + ".jpg";
        return photos.save(Photo.create(
                character, null, originalFileName, stored, "image/jpeg", 16,
                "/uploads/photos/" + stored));
    }

    private WorldPlacedObject emptyTarget(Character character, String key, int x, int y) {
        WorldChange change = changes.save(WorldChange.template(
                character, WorldCategory.NATURE, WorldAssetType.FARM_PLOT_EMPTY,
                key + "-" + UUID.randomUUID(), "테스트 빈 밭", x * 48, y * 48));
        return objects.save(WorldPlacedObject.create(
                change, WorldAssetType.FARM_PLOT_EMPTY, TerrainType.SOIL,
                HabitatType.DECORATION_ONLY, x * 48, y * 48));
    }

    private void move(Character character, int x, int y) {
        WorldPlayerPosition position = positions.findByCharacterId(character.getId())
                .orElseGet(() -> positions.save(WorldPlayerPosition.create(character, x, y)));
        position.moveTo(x, y);
    }

    private PlantMemoryResponse readResponse(String json) {
        try {
            return objectMapper.readValue(json, PlantMemoryResponse.class);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static Stream<Arguments> plantableMappings() {
        return Stream.of(
                Arguments.of("flower-memory.jpg", "FLOWER", WorldAssetType.FARM_FLOWER),
                Arguments.of("carrot-memory.jpg", "CARROT", WorldAssetType.FARM_CARROT),
                Arguments.of("tomato-memory.jpg", "TOMATO", WorldAssetType.FARM_TOMATO),
                Arguments.of("vegetable-memory.jpg", "VEGETABLE", WorldAssetType.FARM_VEGETABLE),
                Arguments.of("plant-memory.jpg", "PLANT", WorldAssetType.FARM_VEGETABLE));
    }

    private static Stream<Arguments> outOfRangePositions() {
        return Stream.of(
                Arguments.of(3, 9),
                Arguments.of(2, 8),
                Arguments.of(3, 7));
    }

    private record Fixture(User user, Character character, WorldPlacedObject target, String token) {
    }
}
