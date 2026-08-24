package com.projecteden.world.ecology;

import static org.assertj.core.api.Assertions.assertThat;

import com.projecteden.ai.domain.Recognition;
import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.ai.repository.RecognitionRepository;
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
import com.projecteden.world.chunk.WorldChunkQueryService;
import com.projecteden.world.chunk.WorldChunkRegionType;
import com.projecteden.world.chunk.WorldChunkRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PhotoEcologyPlacementIntegrationTests {
    @Autowired WorldEcologyService ecology;
    @Autowired PhotoEcologyPlacementService placement;
    @Autowired WorldChunkQueryService chunkQuery;
    @Autowired WorldChunkRepository chunks;
    @Autowired WorldPlacedObjectRepository objects;
    @Autowired WorldRepository worlds;
    @Autowired UserRepository users;
    @Autowired CharacterRepository characters;
    @Autowired PhotoRepository photos;
    @Autowired RecognitionRepository recognitions;
    @Autowired PasswordEncoder passwords;

    @Test
    void placesDogInDiscoveredPreferredMeadowAndRetryKeepsIdentityAndCoordinate() {
        Fixture fixture = fixture("dog-meadow");
        ecology.stateForUser(fixture.user().getId());
        discover(fixture, -1, 0);
        Recognition recognition = recognition(fixture.character(), RecognizedObject.DOG);

        WorldChangeResult first = ecology.createFor(recognition);
        WorldPlacedObject placed = objects.findByWorldChangeId(first.worldChangeId()).getFirst();
        String coordinate = placed.getPositionX() + ":" + placed.getPositionY();
        WorldChangeResult retry = ecology.createFor(recognition);

        assertThat(first.ecologyPlacement().applied()).isTrue();
        assertThat(first.ecologyPlacement().regionType()).isEqualTo(WorldChunkRegionType.MEADOW);
        assertThat(first.ecologyPlacement().chunkX()).isEqualTo(-1);
        assertThat(first.spawnedObjectIds()).containsExactly(placed.getId());
        assertThat(retry.worldChangeId()).isEqualTo(first.worldChangeId());
        assertThat(retry.spawnedObjectIds()).containsExactly(placed.getId());
        assertThat(objects.findByWorldChangeId(first.worldChangeId()).getFirst())
                .extracting(object -> object.getPositionX() + ":" + object.getPositionY()).isEqualTo(coordinate);
    }

    @Test
    void birdUsesDiscoveredPondShoreWithoutOccupyingWater() {
        Fixture fixture = fixture("bird-pond");
        ecology.stateForUser(fixture.user().getId());
        discover(fixture, 1, 2);

        WorldChangeResult result = ecology.createFor(recognition(fixture.character(), RecognizedObject.BIRD));
        WorldPlacedObject bird = objects.findByWorldChangeId(result.worldChangeId()).getFirst();

        assertThat(result.ecologyPlacement().regionType()).isEqualTo(WorldChunkRegionType.POND);
        assertThat(result.ecologyPlacement().spawnZone()).isIn("SHORE", "REED_EDGE");
        assertThat(bird.getTerrain()).isNotIn(TerrainType.WATER, TerrainType.BUILDING, TerrainType.SOIL);
        assertThat(bird.getHabitat()).isEqualTo(HabitatType.AIR);
    }

    @Test
    void unknownRecognitionPersistsAuditableNonPlacementWithoutInventedObject() {
        Fixture fixture = fixture("unknown");
        ecology.stateForUser(fixture.user().getId());

        WorldChangeResult result = ecology.createFor(recognition(fixture.character(), RecognizedObject.UNKNOWN));

        assertThat(result.villageChanged()).isFalse();
        assertThat(result.spawnedObjectIds()).isEmpty();
        assertThat(result.ecologyPlacement().applied()).isFalse();
        assertThat(result.ecologyPlacement().reason()).isEqualTo(EcologyPlacementReason.PROFILE_NOT_PLACEABLE);
        assertThat(objects.findByWorldChangeId(result.worldChangeId())).isEmpty();
    }

    @Test
    void differentPhotosAccumulateAsDistinctObjectsWithoutDuplicateTile() {
        Fixture fixture = fixture("different-photos");
        ecology.stateForUser(fixture.user().getId());
        discover(fixture, -1, 0);

        WorldChangeResult first = ecology.createFor(recognition(fixture.character(), RecognizedObject.DOG));
        WorldChangeResult second = ecology.createFor(recognition(fixture.character(), RecognizedObject.DOG));

        assertThat(first.spawnedObjectIds()).hasSize(1);
        assertThat(second.spawnedObjectIds()).hasSize(1);
        assertThat(second.spawnedObjectIds()).doesNotContainAnyElementsOf(first.spawnedObjectIds());
        assertThat(objects.findByCharacterIdOrderByIdAsc(fixture.character().getId()).stream()
                .filter(object -> object.getWorldChange().getRecognition() != null)
                .map(object -> object.getPositionX() + ":" + object.getPositionY()))
                .doesNotHaveDuplicates();
    }

    @Test
    void undiscoveredChunksAreNeverSelectedOrEnteredByPhotoAnimals() {
        Fixture fixture = fixture("undiscovered-movement");
        ecology.stateForUser(fixture.user().getId());
        discover(fixture, -1, 0);

        WorldChangeResult result = ecology.createFor(recognition(fixture.character(), RecognizedObject.DOG));
        WorldPlacedObject dog = objects.findByWorldChangeId(result.worldChangeId()).getFirst();

        assertThat(result.ecologyPlacement().chunkX()).isEqualTo(-1);
        assertThat(result.ecologyPlacement().chunkY()).isZero();
        assertThat(placement.movementAllowed(
                dog, fixture.world(), result.ecologyPlacement().chunkX() * 8, result.ecologyPlacement().chunkY() * 8))
                .isTrue();
        assertThat(placement.movementAllowed(dog, fixture.world(), -8, -8)).isFalse();
    }

    @Test
    void boundedCapacityReturnsExplicitFailureWithoutDeletingExistingAnimals() {
        Fixture fixture = fixture("capacity");
        ecology.stateForUser(fixture.user().getId());
        discover(fixture, -1, 0);
        java.util.List<WorldChangeResult> results = new java.util.ArrayList<>();

        for (int index = 0; index < 30; index++) {
            results.add(ecology.createFor(recognition(fixture.character(), RecognizedObject.DOG)));
        }

        long applied = results.stream().filter(result -> result.ecologyPlacement().applied()).count();
        assertThat(applied).isPositive().isLessThan(30);
        assertThat(results.stream().filter(result -> !result.ecologyPlacement().applied())
                .map(result -> result.ecologyPlacement().reason()))
                .contains(EcologyPlacementReason.CAPACITY_REACHED);
        assertThat(objects.findByCharacterIdOrderByIdAsc(fixture.character().getId()).stream()
                .filter(object -> object.getWorldChange().getRecognition() != null))
                .hasSize((int) applied);
    }

    private void discover(Fixture fixture, int chunkX, int chunkY) {
        chunkQuery.chunksForUser(fixture.user().getId(), chunkX, chunkY, 0);
        var chunk = chunks.findByWorldIdAndChunkXAndChunkY(fixture.world().getId(), chunkX, chunkY).orElseThrow();
        chunk.discover(LocalDateTime.of(2026, 1, 1, 0, 0));
        chunks.flush();
    }

    private Fixture fixture(String suffix) {
        User user = users.save(new User("ecology-" + suffix + "@example.com", passwords.encode("password123"), "ecology-" + suffix));
        Character character = characters.save(Character.create(user, "생태", CharacterGender.NONE,
                HairStyle.PIXEL_CUT, "brown", Outfit.ROBE, CharacterJob.WIZARD));
        World world = worlds.saveAndFlush(World.create(character, character.getId()));
        return new Fixture(user, character, world);
    }

    private Recognition recognition(Character character, RecognizedObject type) {
        String stored = UUID.randomUUID() + ".jpg";
        Photo photo = photos.save(Photo.create(character, null, "ecology.jpg", stored,
                "image/jpeg", 10, "/uploads/photos/" + stored));
        return recognitions.saveAndFlush(Recognition.create(photo, type, 95, true));
    }

    private record Fixture(User user, Character character, World world) { }
}
