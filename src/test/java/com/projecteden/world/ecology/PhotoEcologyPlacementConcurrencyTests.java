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
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:photo_ecology_concurrency;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PhotoEcologyPlacementConcurrencyTests {
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
    void fiveConcurrentCallsForSamePhotoProduceOneTerminalObjectWithoutDeadlock() throws Exception {
        User user = users.save(new User("ecology-race@example.com", passwords.encode("password123"), "ecology-race"));
        Character character = characters.save(Character.create(user, "경쟁", CharacterGender.NONE,
                HairStyle.PIXEL_CUT, "brown", Outfit.ROBE, CharacterJob.WIZARD));
        worlds.saveAndFlush(World.create(character, character.getId()));
        ecology.stateForUser(user.getId());
        Recognition recognition = recognition(character, RecognizedObject.DOG);

        List<WorldChangeResult> results;
        try (var executor = Executors.newFixedThreadPool(5)) {
            var futures = java.util.stream.IntStream.range(0, 5)
                    .mapToObj(index -> executor.submit(() -> ecology.createFor(recognition)))
                    .toList();
            results = futures.stream().map(future -> {
                try { return future.get(); }
                catch (Exception exception) { throw new IllegalStateException(exception); }
            }).toList();
        }

        assertThat(results).extracting(WorldChangeResult::worldChangeId).containsOnly(results.getFirst().worldChangeId());
        assertThat(results).extracting(WorldChangeResult::spawnedObjectIds).containsOnly(results.getFirst().spawnedObjectIds());
        assertThat(changes.findByRecognitionId(recognition.getId())).isPresent();
        assertThat(objects.findByWorldChangeId(results.getFirst().worldChangeId())).hasSize(1);
    }

    @Test
    void fiveDifferentPhotosSerializeOnWorldLockWithoutDuplicateCoordinates() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = users.save(new User("ecology-distinct-" + suffix + "@example.com", passwords.encode("password123"), "ecology-distinct-" + suffix));
        Character character = characters.save(Character.create(user, "다섯 기억", CharacterGender.NONE,
                HairStyle.PIXEL_CUT, "brown", Outfit.ROBE, CharacterJob.WIZARD));
        worlds.saveAndFlush(World.create(character, character.getId()));
        ecology.stateForUser(user.getId());
        List<Recognition> recognitionSet = java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> recognition(character, RecognizedObject.DOG)).toList();

        List<WorldChangeResult> results;
        try (var executor = Executors.newFixedThreadPool(5)) {
            var futures = recognitionSet.stream()
                    .map(recognition -> executor.submit(() -> ecology.createFor(recognition)))
                    .toList();
            results = futures.stream().map(future -> {
                try { return future.get(); }
                catch (Exception exception) { throw new IllegalStateException(exception); }
            }).toList();
        }

        assertThat(results).allSatisfy(result -> assertThat(result.ecologyPlacement().applied()).isTrue());
        assertThat(results).extracting(WorldChangeResult::worldChangeId).doesNotHaveDuplicates();
        assertThat(objects.findByCharacterIdOrderByIdAsc(character.getId()).stream()
                .filter(object -> object.getWorldChange().getRecognition() != null)
                .map(object -> object.getPositionX() + ":" + object.getPositionY()))
                .hasSize(5).doesNotHaveDuplicates();
    }

    private Recognition recognition(Character character, RecognizedObject type) {
        String stored = UUID.randomUUID() + ".jpg";
        Photo photo = photos.save(Photo.create(character, null, "race.jpg", stored,
                "image/jpeg", 10, "/uploads/photos/" + stored));
        return recognitions.saveAndFlush(Recognition.create(photo, type, 95, true));
    }
}
