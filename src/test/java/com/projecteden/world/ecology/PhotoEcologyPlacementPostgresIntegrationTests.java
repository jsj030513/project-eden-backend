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
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@ActiveProfiles("test")
@Testcontainers
class PhotoEcologyPlacementPostgresIntegrationTests {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("eden_phase4b1_ecology")
            .withUsername("eden_test")
            .withPassword("eden_test");

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired WorldEcologyService ecology;
    @Autowired WorldPlacedObjectRepository objects;
    @Autowired WorldChangeRepository changes;
    @Autowired WorldRepository worlds;
    @Autowired UserRepository users;
    @Autowired CharacterRepository characters;
    @Autowired PhotoRepository photos;
    @Autowired RecognitionRepository recognitions;
    @Autowired PasswordEncoder passwords;

    @Test
    void sameRecognitionFiveThreadsProduceOneTerminalChangeAndObject() throws Exception {
        Fixture fixture = fixture("same");
        Recognition recognition = recognition(fixture.character(), RecognizedObject.DOG);

        long started = System.nanoTime();
        List<WorldChangeResult> results = concurrent(5, () -> ecology.createFor(recognition));
        long wallMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        Long changeId = results.getFirst().worldChangeId();
        Long objectId = results.getFirst().spawnedObjectIds().getFirst();
        assertThat(results).extracting(WorldChangeResult::worldChangeId).containsOnly(changeId);
        assertThat(results).extracting(WorldChangeResult::spawnedObjectIds)
                .containsOnly(List.of(objectId));
        assertThat(changes.findByRecognitionId(recognition.getId())).isPresent();
        assertThat(objects.findByWorldChangeId(changeId)).hasSize(1);

        long changesBeforeRetry = changes.count();
        long objectsBeforeRetry = objects.count();
        WorldChangeResult retry = ecology.createFor(recognition);
        assertThat(retry.worldChangeId()).isEqualTo(changeId);
        assertThat(retry.spawnedObjectIds()).containsExactly(objectId);
        assertThat(changes.count()).isEqualTo(changesBeforeRetry);
        assertThat(objects.count()).isEqualTo(objectsBeforeRetry);
        System.out.println("PHASE4B1_SAME_RECOGNITION_POSTGRES threads=5 wallMs=" + wallMillis
                + " worldChanges=1 objects=1 retryDelta=0 deadlocks=0");
    }

    @Test
    void fiveDifferentRecognitionsSerializeWithoutDuplicateCoordinates() throws Exception {
        Fixture fixture = fixture("different");
        List<Recognition> recognitionSet = java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> recognition(fixture.character(), RecognizedObject.DOG))
                .toList();

        long started = System.nanoTime();
        List<WorldChangeResult> results = concurrentTasks(recognitionSet.stream()
                .<Callable<WorldChangeResult>>map(recognition -> () -> ecology.createFor(recognition))
                .toList());
        long wallMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(results).allSatisfy(result -> assertThat(result.ecologyPlacement().applied()).isTrue());
        assertThat(results).extracting(WorldChangeResult::worldChangeId).doesNotHaveDuplicates();
        assertThat(results).extracting(WorldChangeResult::spawnedObjectIds).doesNotHaveDuplicates();
        assertThat(objects.findByCharacterIdOrderByIdAsc(fixture.character().getId()).stream()
                .filter(object -> object.getWorldChange().getRecognition() != null)
                .map(object -> object.getPositionX() + ":" + object.getPositionY()))
                .hasSize(5).doesNotHaveDuplicates();
        System.out.println("PHASE4B1_DIFFERENT_RECOGNITION_POSTGRES threads=5 wallMs=" + wallMillis
                + " worldChanges=5 objects=5 duplicateCoordinates=0 deadlocks=0");
    }

    private <T> List<T> concurrent(int count, Callable<T> task) throws Exception {
        return concurrentTasks(java.util.stream.IntStream.range(0, count)
                .<Callable<T>>mapToObj(index -> task).toList());
    }

    private <T> List<T> concurrentTasks(List<Callable<T>> tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(tasks.size())) {
            var futures = tasks.stream().map(task -> executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return task.call();
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return futures.stream().map(future -> {
                try {
                    return future.get(20, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }).toList();
        }
    }

    private Fixture fixture(String label) {
        String suffix = label + "-" + UUID.randomUUID().toString().substring(0, 8);
        User user = users.save(new User("ecology-pg-" + suffix + "@example.com",
                passwords.encode("password123"), "ecology-pg-" + suffix));
        Character character = characters.save(Character.create(user, "생태", CharacterGender.NONE,
                HairStyle.PIXEL_CUT, "brown", Outfit.ROBE, CharacterJob.WIZARD));
        World world = worlds.saveAndFlush(World.create(character, character.getId()));
        ecology.stateForUser(user.getId());
        return new Fixture(user, character, world);
    }

    private Recognition recognition(Character character, RecognizedObject type) {
        String stored = UUID.randomUUID() + ".jpg";
        Photo photo = photos.save(Photo.create(character, null, "ecology-postgres.jpg", stored,
                "image/jpeg", 10, "/uploads/photos/" + stored));
        return recognitions.saveAndFlush(Recognition.create(photo, type, 95, true));
    }

    private record Fixture(User user, Character character, World world) { }
}
