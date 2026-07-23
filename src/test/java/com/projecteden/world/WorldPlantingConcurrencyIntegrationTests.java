package com.projecteden.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.projecteden.character.domain.Character;
import com.projecteden.character.domain.CharacterGender;
import com.projecteden.character.domain.CharacterJob;
import com.projecteden.character.domain.HairStyle;
import com.projecteden.character.domain.Outfit;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.common.exception.DuplicateResourceException;
import com.projecteden.photo.domain.Photo;
import com.projecteden.photo.repository.PhotoRepository;
import com.projecteden.ai.repository.RecognitionRepository;
import com.projecteden.memorytaxonomy.repository.MemoryClassificationRepository;
import com.projecteden.village.repository.VillageMemoryRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.ecology.PlantMemoryRequest;
import com.projecteden.world.ecology.PlantMemoryResponse;
import com.projecteden.world.ecology.HabitatType;
import com.projecteden.world.ecology.TerrainType;
import com.projecteden.world.ecology.WorldAssetType;
import com.projecteden.world.ecology.WorldCategory;
import com.projecteden.world.ecology.WorldChange;
import com.projecteden.world.ecology.WorldChangeRepository;
import com.projecteden.world.ecology.WorldEcologyService;
import com.projecteden.world.ecology.WorldPlacedObject;
import com.projecteden.world.ecology.WorldPlacedObjectRepository;
import com.projecteden.world.ecology.WorldPlayerPosition;
import com.projecteden.world.ecology.WorldPlayerPositionRepository;
import com.projecteden.world.ecology.WorldPlantingService;
import com.projecteden.world.repository.WorldRepository;

@SpringBootTest
@ActiveProfiles("test")
class WorldPlantingConcurrencyIntegrationTests {

    private static final Set<String> SEEDED_REFERENCE_TABLES = Set.of(
            "ACHIEVEMENTS",
            "TITLES",
            "MEMORY_TAXONOMY_CATEGORIES",
            "MEMORY_TAGS");

    @Autowired private WorldPlantingService planting;
    @Autowired private WorldEcologyService ecology;
    @Autowired private UserRepository users;
    @Autowired private CharacterRepository characters;
    @Autowired private WorldRepository worlds;
    @Autowired private PhotoRepository photos;
    @Autowired private WorldPlacedObjectRepository objects;
    @Autowired private WorldChangeRepository changes;
    @Autowired private RecognitionRepository recognitions;
    @Autowired private MemoryClassificationRepository classifications;
    @Autowired private VillageMemoryRepository memories;
    @Autowired private WorldPlayerPositionRepository positions;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    @AfterEach
    void clearCommittedConcurrencyFixture() {
        if (isPostgreSql()) {
            List<String> tables = jdbcTemplate.queryForList("""
                    SELECT tablename
                    FROM pg_tables
                    WHERE schemaname = 'public'
                    ORDER BY tablename
                    """, String.class).stream()
                    .filter(table -> !"flyway_schema_history".equalsIgnoreCase(table))
                    .filter(table -> !SEEDED_REFERENCE_TABLES.contains(table.toUpperCase()))
                    .toList();
            if (!tables.isEmpty()) {
                String quotedTables = tables.stream()
                        .map(table -> "\"" + table.replace("\"", "\"\"") + "\"")
                        .reduce((left, right) -> left + ", " + right)
                        .orElseThrow();
                jdbcTemplate.execute("TRUNCATE TABLE " + quotedTables + " RESTART IDENTITY CASCADE");
            }
            return;
        }
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            jdbcTemplate.queryForList("""
                    SELECT TABLE_NAME
                    FROM INFORMATION_SCHEMA.TABLES
                    WHERE TABLE_SCHEMA = 'PUBLIC'
                      AND TABLE_TYPE = 'BASE TABLE'
                    """, String.class).stream()
                    .filter(table -> !SEEDED_REFERENCE_TABLES.contains(table))
                    .forEach(table -> jdbcTemplate.execute("TRUNCATE TABLE \"" + table + "\""));
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    @Test
    void serializesConcurrentPlantingAndKeepsExactlyOneWinner() throws Exception {
        Fixture fixture = fixture("different-photos-same-target");
        Photo firstPhoto = photo(fixture.character(), "flower-concurrent.jpg");
        Photo secondPhoto = photo(fixture.character(), "carrot-concurrent.jpg");
        long changesBefore = changes.count();
        long objectsBefore = objects.count();
        long recognitionsBefore = recognitions.count();
        long classificationsBefore = classifications.count();
        long memoriesBefore = memories.count();
        Long unrelatedObjectId = ecology.stateForUser(fixture.user().getId()).placedObjects().stream()
                .filter(object -> object.assetType() == WorldAssetType.COMMUNITY_HOUSE)
                .findFirst()
                .orElseThrow()
                .id();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            List<CompletableFuture<Object>> requests = List.of(
                    request(executor, start, fixture.user().getId(),
                            new PlantMemoryRequest(firstPhoto.getId(), fixture.target().getId(), 3, 9)),
                    request(executor, start, fixture.user().getId(),
                            new PlantMemoryRequest(secondPhoto.getId(), fixture.target().getId(), 3, 9)));
            start.countDown();
            List<Object> results = requests.stream().map(CompletableFuture::join).toList();

            assertThat(results).filteredOn(PlantMemoryResponse.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(DuplicateResourceException.class::isInstance).hasSize(1)
                    .allSatisfy(error -> assertThat(((DuplicateResourceException) error).getMessage())
                            .isEqualTo("TARGET_ALREADY_PLANTED"));
            assertThat(changes.count()).isEqualTo(changesBefore + 1);
            assertThat(objects.count()).isEqualTo(objectsBefore + 1);
            assertThat(recognitions.count()).isEqualTo(recognitionsBefore + 1);
            assertThat(classifications.count()).isEqualTo(classificationsBefore + 1);
            assertThat(memories.count()).isEqualTo(memoriesBefore + 1);
            assertThat(changes.findByTargetObjectId(fixture.target().getId())).isPresent();
            assertThat(objects.findByWorldChangeId(
                    changes.findByTargetObjectId(fixture.target().getId()).orElseThrow().getId()))
                    .hasSize(1);
            assertThat(objects.findById(fixture.target().getId())).isPresent()
                    .get().extracting(WorldPlacedObject::getAssetType)
                    .isEqualTo(WorldAssetType.FARM_PLOT_EMPTY);
            assertThat(objects.findById(unrelatedObjectId)).isPresent();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void returnsOneCreatedAndOneIdempotentResultForTheSamePhotoAndTarget() throws Exception {
        Fixture fixture = fixture("same-photo-same-target");
        Photo photo = photo(fixture.character(), "flower-same-request.jpg");
        PlantMemoryRequest command = new PlantMemoryRequest(photo.getId(), fixture.target().getId(), 3, 9);
        long changesBefore = changes.count();
        long objectsBefore = objects.count();
        long recognitionsBefore = recognitions.count();
        long classificationsBefore = classifications.count();
        long memoriesBefore = memories.count();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            List<CompletableFuture<Object>> requests = List.of(
                    request(executor, start, fixture.user().getId(), command),
                    request(executor, start, fixture.user().getId(), command));
            start.countDown();
            List<Object> results = requests.stream().map(CompletableFuture::join).toList();

            assertThat(results).allSatisfy(result -> assertThat(result).isInstanceOf(PlantMemoryResponse.class));
            List<PlantMemoryResponse> responses = results.stream()
                    .map(PlantMemoryResponse.class::cast)
                    .toList();
            assertThat(responses).hasSize(2);
            assertThat(responses.get(1)).isEqualTo(responses.get(0));
            assertThat(changes.count()).isEqualTo(changesBefore + 1);
            assertThat(objects.count()).isEqualTo(objectsBefore + 1);
            assertThat(recognitions.count()).isEqualTo(recognitionsBefore + 1);
            assertThat(classifications.count()).isEqualTo(classificationsBefore + 1);
            assertThat(memories.count()).isEqualTo(memoriesBefore + 1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void associatesOneTargetWhenTheSamePhotoRacesForDifferentTargets() throws Exception {
        Fixture fixture = fixture("same-photo-different-targets");
        WorldChange secondTemplateChange = changes.save(WorldChange.template(
                fixture.character(), WorldCategory.NATURE, WorldAssetType.FARM_PLOT_EMPTY,
                "CONCURRENCY_SECOND_EMPTY", "동시성 검증용 빈 밭", 5 * 48, 9 * 48));
        WorldPlacedObject secondTarget = objects.save(WorldPlacedObject.create(
                secondTemplateChange, WorldAssetType.FARM_PLOT_EMPTY,
                TerrainType.SOIL, HabitatType.DECORATION_ONLY, 5 * 48, 9 * 48));
        move(fixture.character(), 4, 9);
        Photo photo = photo(fixture.character(), "flower-different-targets.jpg");
        long changesBefore = changes.count();
        long objectsBefore = objects.count();
        long recognitionsBefore = recognitions.count();
        long classificationsBefore = classifications.count();
        long memoriesBefore = memories.count();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            List<CompletableFuture<Object>> requests = List.of(
                    request(executor, start, fixture.user().getId(),
                            new PlantMemoryRequest(photo.getId(), fixture.target().getId(), 3, 9)),
                    request(executor, start, fixture.user().getId(),
                            new PlantMemoryRequest(photo.getId(), secondTarget.getId(), 5, 9)));
            start.countDown();
            List<Object> results = requests.stream().map(CompletableFuture::join).toList();

            assertThat(results).filteredOn(PlantMemoryResponse.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(DuplicateResourceException.class::isInstance).hasSize(1)
                    .allSatisfy(error -> assertThat(((DuplicateResourceException) error).getMessage())
                            .isEqualTo("PHOTO_ALREADY_EXPRESSED"));
            assertThat(recognitions.count()).isEqualTo(recognitionsBefore + 1);
            assertThat(classifications.count()).isEqualTo(classificationsBefore + 1);
            assertThat(memories.count()).isEqualTo(memoriesBefore + 1);
            assertThat(changes.count()).isEqualTo(changesBefore + 1);
            assertThat(objects.count()).isEqualTo(objectsBefore + 1);

            List<WorldChange> targetedChanges = List.of(fixture.target().getId(), secondTarget.getId()).stream()
                    .map(changes::findByTargetObjectId)
                    .flatMap(java.util.Optional::stream)
                    .toList();
            assertThat(targetedChanges).singleElement();
            WorldChange winner = targetedChanges.getFirst();
            Long loserTargetId = winner.getTargetObject().getId().equals(fixture.target().getId())
                    ? secondTarget.getId()
                    : fixture.target().getId();
            assertThat(objects.findByWorldChangeId(winner.getId())).singleElement();
            assertThat(objects.findById(loserTargetId)).isPresent()
                    .get().extracting(WorldPlacedObject::getAssetType)
                    .isEqualTo(WorldAssetType.FARM_PLOT_EMPTY);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void committedResponseLossRetryLeavesEveryPersistentCountUnchanged() {
        Fixture fixture = fixture("response-loss-retry");
        Photo photo = photo(fixture.character(), "flower-response-loss.jpg");
        PlantMemoryRequest command = new PlantMemoryRequest(photo.getId(), fixture.target().getId(), 3, 9);

        PlantMemoryResponse first = planting.plant(fixture.user().getId(), command);
        long changesAfterCommit = changes.count();
        long objectsAfterCommit = objects.count();
        long recognitionsAfterCommit = recognitions.count();
        long classificationsAfterCommit = classifications.count();
        long memoriesAfterCommit = memories.count();

        PlantMemoryResponse retry = planting.plant(fixture.user().getId(), command);

        assertThat(retry).isEqualTo(first);
        assertThat(changes.count()).isEqualTo(changesAfterCommit);
        assertThat(objects.count()).isEqualTo(objectsAfterCommit);
        assertThat(recognitions.count()).isEqualTo(recognitionsAfterCommit);
        assertThat(classifications.count()).isEqualTo(classificationsAfterCommit);
        assertThat(memories.count()).isEqualTo(memoriesAfterCommit);
    }

    private CompletableFuture<Object> request(
            ExecutorService executor,
            CountDownLatch start,
            Long userId,
            PlantMemoryRequest command) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return planting.plant(userId, command);
            } catch (DuplicateResourceException exception) {
                return exception;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }, executor);
    }

    private Fixture fixture(String name) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        User user = users.save(new User(
                "planting-" + name + "-" + unique + "@example.com",
                passwordEncoder.encode("password123"),
                ("planting-" + unique).substring(0, 17)));
        Character character = characters.save(Character.create(
                user, "에덴", CharacterGender.NONE, HairStyle.PIXEL_CUT,
                "brown", Outfit.ROBE, CharacterJob.WIZARD));
        worlds.save(World.create(character, unique.hashCode()));
        WorldPlacedObject target = ecology.stateForUser(user.getId()).placedObjects().stream()
                .filter(object -> object.assetType() == WorldAssetType.FARM_PLOT_EMPTY)
                .findFirst()
                .flatMap(object -> objects.findById(object.id()))
                .orElseThrow();
        move(character, 3, 8);
        return new Fixture(user, character, target);
    }

    private void move(Character character, int x, int y) {
        WorldPlayerPosition position = positions.findByCharacterId(character.getId()).orElseThrow();
        position.moveTo(x, y);
        positions.save(position);
    }

    private boolean isPostgreSql() {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql");
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Cannot inspect concurrency test database", exception);
        }
    }

    private Photo photo(Character character, String originalFileName) {
        String stored = UUID.randomUUID() + ".jpg";
        return photos.save(Photo.create(
                character, null, originalFileName, stored, "image/jpeg", 16,
                "/uploads/photos/" + stored));
    }

    private record Fixture(User user, Character character, WorldPlacedObject target) {
    }
}
