package com.projecteden.world;

import static org.assertj.core.api.Assertions.assertThat;

import com.projecteden.character.domain.Character;
import com.projecteden.character.domain.CharacterGender;
import com.projecteden.character.domain.CharacterJob;
import com.projecteden.character.domain.HairStyle;
import com.projecteden.character.domain.Outfit;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.world.ecology.WorldEcologyService;
import com.projecteden.world.npc.NpcCheckpointScheduler;
import com.projecteden.world.npc.NpcRuntimeStateRepository;
import com.projecteden.world.npc.NpcRuntimeService;
import com.projecteden.world.repository.WorldRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        // Reuse isolated H2/mock-provider settings as configuration, NOT an active profile.
        "spring.config.import=classpath:application-test.yml",
        "spring.datasource.url=jdbc:h2:mem:npc_cadence;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("npc-cadence")
@Import(NpcCheckpointPersistenceCadenceIntegrationTests.CadenceConfiguration.class)
class NpcCheckpointPersistenceCadenceIntegrationTests {
    @TestConfiguration(proxyBeanMethods = false)
    static class CadenceConfiguration {
        @Bean @Primary NpcCadenceTestSupport.MutableClock cadenceClock() {
            return new NpcCadenceTestSupport.MutableClock();
        }
        @Bean NpcCadenceTestSupport.CapturingScheduler capturingScheduler() {
            return new NpcCadenceTestSupport.CapturingScheduler();
        }
        @Bean TaskScheduler taskScheduler(NpcCadenceTestSupport.CapturingScheduler capture) {
            return capture.scheduler;
        }
    }

    @Autowired NpcCadenceTestSupport.MutableClock clock;
    @Autowired NpcCadenceTestSupport.CapturingScheduler capture;
    @Autowired NpcCheckpointScheduler scheduler;
    @Autowired NpcRuntimeService runtime;
    @Autowired WorldEcologyService ecology;
    @Autowired NpcRuntimeStateRepository states;
    @Autowired WorldRepository worlds;
    @Autowired UserRepository users;
    @Autowired CharacterRepository characters;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager entityManager;

    record PersistedNpc(long objectId, long version, LocalDateTime checkpointAt) { }

    @Test
    void scheduledCallbackPersistsOnlyAtFiveSecondCadenceBoundaries() {
        var transactions = new TransactionTemplate(transactionManager);
        Long worldId = transactions.execute(status -> {
            User user = users.save(new User("npc-cadence@example.com", "encoded-password", "cadence"));
            Character character = characters.save(Character.create(user, "에덴", CharacterGender.NONE,
                    HairStyle.PIXEL_CUT, "brown", Outfit.ROBE, CharacterJob.WIZARD));
            ecology.stateForUser(user.getId());
            return worlds.findByCharacterId(character.getId()).orElseThrow().getId();
        });
        List<PersistedNpc> bootstrap = readCommitted(worldId);
        assertThat(bootstrap).hasSize(4).extracting(PersistedNpc::objectId).doesNotHaveDuplicates();
        assertThat(bootstrap).extracting(PersistedNpc::checkpointAt).containsOnlyNulls();
        assertThat(capture.registrations()).hasSize(1);
        // Run the registered callback deterministically; Spring registration is
        // covered separately, and this test covers callback -> real service -> DB.
        Runnable callback = capture.registrations().getFirst().callback();
        callback.run();
        List<PersistedNpc> first = readCommitted(worldId);
        assertAdvanced(bootstrap, first);

        clock.advance(Duration.ofMillis(4999));
        callback.run();
        assertThat(readCommitted(worldId)).isEqualTo(first);
        // Due-world filtering already suppresses this callback. Exercise the
        // independent service gate too, so removing it cannot escape coverage.
        runtime.checkpointWorld(worldId);
        assertThat(readCommitted(worldId)).isEqualTo(first);

        clock.advance(Duration.ofMillis(1));
        callback.run();
        List<PersistedNpc> boundary = readCommitted(worldId);
        assertAdvanced(first, boundary);

        callback.run();
        assertThat(readCommitted(worldId)).isEqualTo(boundary);
        runtime.checkpointWorld(worldId);
        assertThat(readCommitted(worldId)).isEqualTo(boundary);
    }

    private List<PersistedNpc> readCommitted(Long worldId) {
        // No outer test transaction: callback service transactions have committed.
        // Clear the fresh transaction's persistence context before querying DB.
        return new TransactionTemplate(transactionManager).execute(status -> {
            entityManager.clear();
            return states.findByWorldIdOrderByNpcObjectIdAsc(worldId).stream()
                    .map(state -> new PersistedNpc(state.getNpcObject().getId(),
                            state.getStateVersion(), state.getLastCheckpointAt())).toList();
        });
    }

    private void assertAdvanced(List<PersistedNpc> before, List<PersistedNpc> after) {
        assertThat(after).hasSize(4).extracting(PersistedNpc::objectId)
                .containsExactlyElementsOf(before.stream().map(PersistedNpc::objectId).toList())
                .doesNotHaveDuplicates();
        for (int index = 0; index < before.size(); index++) {
            assertThat(after.get(index).version()).isEqualTo(before.get(index).version() + 1);
            assertThat(after.get(index).checkpointAt())
                    .isEqualTo(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        }
    }
}
