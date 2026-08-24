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
import com.projecteden.world.chunk.WorldChunkQueryService;
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
import com.projecteden.world.npc.CanonicalNpcKey;
import com.projecteden.world.npc.NpcCheckpointScheduler;
import com.projecteden.world.npc.NpcRuntimeService;
import com.projecteden.world.npc.NpcRuntimeState;
import com.projecteden.world.npc.NpcRuntimeStateRepository;
import com.projecteden.world.npc.NpcRelationshipService;
import com.projecteden.world.npc.NpcQuestEvent;
import com.projecteden.world.npc.NpcQuestEventRepository;
import com.projecteden.world.npc.NpcQuestEventProcessingStatus;
import com.projecteden.world.npc.NpcQuestEventType;
import com.projecteden.world.npc.NpcQuestStateRepository;
import com.projecteden.world.npc.NpcQuestStatus;
import com.projecteden.world.npc.NpcScheduleRegistry;
import com.projecteden.world.npc.NpcWorldAnchor;
import com.projecteden.world.npc.WorldNpcDialogueService;
import com.projecteden.world.repository.WorldRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.projecteden.world.NpcSqlStatementCounter",
        "spring.flyway.enabled=true"
})
@ActiveProfiles("test")
@Testcontainers
class NpcCheckpointPerformancePostgresIntegrationTests {
    private static final int RUNS = 5;
    private static final LocalDateTime OLD_CHECKPOINT = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("eden_phase4a1_performance")
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
    @Autowired NpcRuntimeService runtime;
    @Autowired NpcRelationshipService relationships;
    @Autowired WorldNpcDialogueService dialogues;
    @Autowired WorldChunkQueryService chunks;
    @Autowired NpcRuntimeStateRepository states;
    @Autowired NpcQuestStateRepository questStates;
    @Autowired NpcQuestEventRepository questEvents;
    @Autowired NpcScheduleRegistry schedules;
    @Autowired WorldPlayerPositionRepository positions;
    @Autowired WorldChangeRepository worldChanges;
    @Autowired WorldPlacedObjectRepository worldObjects;
    @Autowired WorldRepository worlds;
    @Autowired UserRepository users;
    @Autowired CharacterRepository characters;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired Clock clock;

    @Test
    void capturesCheckpointQueryAndLatencyEvidenceWithoutNpcNPlusOne() throws Exception {
        Fixture fixture = fixture();
        var initial = ecology.stateForUser(fixture.user().getId());
        assertThat(initial.npcPositions()).hasSize(4);
        Long worldId = worlds.findByCharacterId(fixture.character().getId()).orElseThrow().getId();

        resetAtHomes(worldId);
        Measurement coldFour = measure(() -> runtime.checkpointWorld(worldId));
        List<Measurement> fourMovement = repeatedMovement(worldId, 4);

        resetAtHomes(worldId);
        runtime.checkpointWorld(worldId);
        Measurement coldNoop = measure(() -> runtime.checkpointWorld(worldId));
        List<Measurement> noop = repeatedNoop(worldId);

        resetForMovers(worldId, 1);
        Measurement coldOne = measure(() -> runtime.checkpointWorld(worldId));
        List<Measurement> oneMovement = repeatedMovement(worldId, 1);
        ConcurrentEvidence concurrent = concurrentEvidence(worldId);

        NpcSqlStatementCounter.reset();
        runtime.projections(worldId);
        var projectionCounts = NpcSqlStatementCounter.snapshot();

        chunks.chunksForUser(fixture.user().getId(), 1, 1, 1);
        NpcSqlStatementCounter.reset();
        chunks.chunksForUser(fixture.user().getId(), 1, 1, 1);
        var chunkCounts = NpcSqlStatementCounter.snapshot();

        relationships.relationships(fixture.user().getId());
        NpcSqlStatementCounter.reset();
        var relationshipSnapshots = relationships.relationships(fixture.user().getId());
        var relationshipCounts = NpcSqlStatementCounter.snapshot();

        DialogueEvidence dialogue = dialogueEvidence(fixture);
        DeferredEvidence deferred = deferredEvidence(fixture);
        long runtimeRows = states.findByWorldIdOrderByNpcObjectIdAsc(worldId).size();
        long distinctObjects = states.findByWorldIdOrderByNpcObjectIdAsc(worldId).stream()
                .map(state -> state.getNpcObject().getId()).distinct().count();

        assertThat(coldFour.elapsedMillis()).isLessThan(2_000);
        assertThat(coldNoop.elapsedMillis()).isLessThan(2_000);
        assertThat(coldOne.elapsedMillis()).isLessThan(2_000);
        assertThat(fourMovement).allMatch(value -> value.elapsedMillis() < 2_000);
        assertThat(oneMovement).allMatch(value -> value.elapsedMillis() < 2_000);
        assertThat(noop).allMatch(value -> value.elapsedMillis() < 2_000);
        assertThat(concurrent.failures()).isZero();
        assertThat(concurrent.versionDeltaPerTrial()).containsOnly(4L);
        assertThat(runtimeRows).isEqualTo(4);
        assertThat(distinctObjects).isEqualTo(4);
        assertThat(projectionCounts.selects()).isLessThanOrEqualTo(1);
        assertThat(relationshipSnapshots).hasSize(4);
        assertThat(relationshipCounts.selects()).isLessThanOrEqualTo(10);
        assertThat(relationshipCounts.inserts()).isZero();
        assertThat(fourMovement).allMatch(value -> value.counts().selects() <= 8);
        assertThat(fourMovement).allMatch(value -> value.counts().updates() <= 4);
        assertThat(dialogue.completion().counts().inserts()).isLessThanOrEqualTo(3);
        assertThat(deferred.one().result().processed()).isEqualTo(1);
        assertThat(deferred.ten().result().selected()).isEqualTo(10);
        assertThat(deferred.noop().result().selected()).isZero();
        assertThat(deferred.concurrentFailures()).isZero();
        assertThat(deferred.duplicateRows()).isZero();

        System.out.println("PHASE4A1_CHECKPOINT_PERF"
                + " postgres=" + POSTGRES.getDockerImageName()
                + " npcs=4 runs=" + RUNS
                + " noopCold=" + coldNoop.compact()
                + " noopWarm=" + summary(noop)
                + " oneMoveCold=" + coldOne.compact()
                + " oneMoveWarm=" + summary(oneMovement)
                + " fourMoveCold=" + coldFour.compact()
                + " fourMoveWarm=" + summary(fourMovement)
                + " concurrent=" + concurrent.compact()
                + " projection=" + projectionCounts
                + " chunks=" + chunkCounts
                + " relationship=" + relationshipCounts
                + " dialogueStart=" + dialogue.start().compact()
                + " dialogueChoice=" + dialogue.choice().compact()
                + " dialogueCompletion=" + dialogue.completion().compact()
                + " deferredOne=" + deferred.one().compact()
                + " deferredTen=" + deferred.ten().compact()
                + " deferredNoop=" + deferred.noop().compact()
                + " deferredFiveThread={ms=" + deferred.concurrentMillis()
                + ",queries=" + deferred.concurrentCounts()
                + ",failures=" + deferred.concurrentFailures() + "}"
                + " jdbcBatching=disabled"
                + " duplicateRows=0 deadlocks=0");
    }

    @Test
    void schedulerProcessesOnlyDueWorldsInFairBoundedPostgresBatches() {
        SchedulerScale one = schedulerScale(1, "one");
        SchedulerScale hundred = schedulerScale(100, "hundred");
        SchedulerScale hundredOne = schedulerScale(101, "hundred-one");
        SchedulerScale sixHundredFiftyFour = schedulerScale(654, "six-fifty-four");

        assertThat(one.processedPerCycle()).containsExactly(1);
        assertThat(hundred.processedPerCycle()).containsExactly(100);
        assertThat(hundredOne.processedPerCycle()).containsExactly(100, 1);
        assertThat(sixHundredFiftyFour.processedPerCycle())
                .containsExactly(100, 100, 100, 100, 100, 100, 54);
        assertThat(List.of(one, hundred, hundredOne, sixHundredFiftyFour))
                .allSatisfy(scale -> {
                    assertThat(scale.remainingDue()).isZero();
                    assertThat(scale.duplicateProcessing()).isZero();
                    assertThat(scale.failures()).isZero();
                });

        System.out.println("PHASE4B1_SCHEDULER_SCALE"
                + " one=" + one.compact()
                + " hundred=" + hundred.compact()
                + " hundredOne=" + hundredOne.compact()
                + " sixHundredFiftyFour=" + sixHundredFiftyFour.compact()
                + " maxPerCycle=100 starvation=0 deadlocks=0");
    }

    private SchedulerScale schedulerScale(int worldCount, String key) {
        markExistingRuntimeCurrent();
        List<Long> worldIds = createDueWorlds(worldCount, key);
        Clock schedulerClock = Clock.fixed(clock.instant(), ZoneOffset.UTC);
        NpcCheckpointScheduler scheduler = new NpcCheckpointScheduler(states, runtime, schedulerClock);
        List<Integer> processed = new ArrayList<>();
        int failures = 0;
        NpcSqlStatementCounter.reset();
        long started = System.nanoTime();
        while (true) {
            int before = dueCount(worldIds, schedulerClock);
            if (before == 0) break;
            scheduler.checkpoint();
            int after = dueCount(worldIds, schedulerClock);
            int delta = before - after;
            if (delta <= 0) {
                failures++;
                break;
            }
            processed.add(delta);
        }
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        List<NpcRuntimeState> createdStates = states.findAll().stream()
                .filter(state -> worldIds.contains(state.getWorld().getId()))
                .toList();
        long duplicateProcessing = createdStates.stream()
                .filter(state -> state.getStateVersion() != 1L)
                .count();
        return new SchedulerScale(
                worldCount, processed, dueCount(worldIds, schedulerClock), duplicateProcessing, failures,
                elapsedMillis, NpcSqlStatementCounter.snapshot());
    }

    private List<Long> createDueWorlds(int count, String key) {
        return transactions().execute(status -> {
            List<Long> result = new ArrayList<>(count);
            LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
            for (int index = 0; index < count; index++) {
                User user = users.save(new User(
                        "scheduler-" + key + "-" + index + "@example.com",
                        "encoded-test-password",
                        "scheduler-" + key + "-" + index));
                Character character = characters.save(Character.create(
                        user, "에덴", CharacterGender.NONE, HairStyle.PIXEL_CUT,
                        "brown", Outfit.ROBE, CharacterJob.WIZARD));
                var world = worlds.save(com.projecteden.world.domain.World.create(character, character.getId()));
                WorldChange change = worldChanges.save(WorldChange.template(
                        character, WorldCategory.MEMORY, WorldAssetType.DEFAULT_NPC_GUIDE,
                        "SCHEDULER_SCALE", "scheduler scale fixture", 48, 48));
                WorldPlacedObject object = worldObjects.save(WorldPlacedObject.create(
                        change, WorldAssetType.DEFAULT_NPC_GUIDE,
                        TerrainType.GRASS, HabitatType.DECORATION_ONLY, 48, 48));
                states.save(NpcRuntimeState.create(
                        world, object, CanonicalNpcKey.NPC_MAYOR,
                        1, 1, "2026-01-01", createdAt));
                result.add(world.getId());
            }
            return result;
        });
    }

    private void markExistingRuntimeCurrent() {
        transactions().executeWithoutResult(ignored -> states.findAll().forEach(state -> state.checkpoint(
                state.getTileX(), state.getTileY(), state.getActivity(), state.getScheduleSlot(),
                state.getScheduleDateKey(), LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))));
    }

    private int dueCount(List<Long> worldIds, Clock schedulerClock) {
        LocalDateTime cutoff = LocalDateTime.ofInstant(
                schedulerClock.instant().minus(NpcRuntimeService.CHECKPOINT_CADENCE), ZoneOffset.UTC);
        return (int) states.findDueWorldIds(cutoff, PageRequest.of(0, 2_000)).stream()
                .filter(worldIds::contains)
                .count();
    }

    private DeferredEvidence deferredEvidence(Fixture fixture) throws Exception {
        ecology.stateForUser(fixture.user().getId());
        var world = worlds.findByCharacterId(fixture.character().getId()).orElseThrow();
        LocalDateTime base = LocalDateTime.of(2026, 7, 30, 12, 0);

        activateCaretakerQuest(fixture, base);
        appendDeferred(fixture, world, "perf-deferred-one", base);
        ReplayMeasurement one = measureReplay(fixture);

        activateCaretakerQuest(fixture, base.plusMinutes(1));
        for (int index = 0; index < 10; index++) {
            appendDeferred(fixture, world, "perf-deferred-ten-" + index, base.plusMinutes(2 + index));
        }
        ReplayMeasurement ten = measureReplay(fixture);
        ReplayMeasurement noop = measureReplay(fixture);

        activateCaretakerQuest(fixture, base.plusMinutes(20));
        appendDeferred(fixture, world, "perf-deferred-concurrent", base.plusMinutes(21));
        CountDownLatch ready = new CountDownLatch(5);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(5);
        AtomicInteger failures = new AtomicInteger();
        NpcSqlStatementCounter.reset();
        long started = System.nanoTime();
        try (var executor = Executors.newFixedThreadPool(5)) {
            for (int index = 0; index < 5; index++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await(5, TimeUnit.SECONDS);
                        relationships.replayPending(fixture.user().getId());
                    } catch (Exception exception) {
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
        }
        long concurrentMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        NpcSqlStatementCounter.Counts concurrentCounts = NpcSqlStatementCounter.snapshot();
        long duplicateRows = questEvents.findByCharacterIdOrderByCreatedAtAscIdAsc(
                        fixture.character().getId()).stream()
                .collect(java.util.stream.Collectors.groupingBy(NpcQuestEvent::getEventKey,
                        java.util.stream.Collectors.counting()))
                .values().stream().filter(count -> count > 1).count();
        assertThat(questEvents.findByCharacterIdOrderByCreatedAtAscIdAsc(
                        fixture.character().getId()).stream()
                .filter(event -> event.getEventKey().equals("perf-deferred-concurrent")))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getProcessingStatus()).isEqualTo(NpcQuestEventProcessingStatus.PROCESSED);
                    assertThat(event.getProcessingAttempts()).isEqualTo(1);
                });
        return new DeferredEvidence(
                one, ten, noop, concurrentMillis, concurrentCounts, failures.get(), duplicateRows);
    }

    private void activateCaretakerQuest(Fixture fixture, LocalDateTime now) {
        transactions().executeWithoutResult(ignored -> {
            var state = questStates.findForUpdate(
                    fixture.character().getId(), "quest.caretaker.animal").orElseThrow();
            if (state.getStatus() == NpcQuestStatus.COMPLETED) state.resetRepeatable(now);
            state.activate(now);
        });
    }

    private void appendDeferred(Fixture fixture, com.projecteden.world.domain.World world,
            String key, LocalDateTime occurredAt) {
        transactions().executeWithoutResult(ignored -> questEvents.save(NpcQuestEvent.create(
                fixture.character(), world, key,
                NpcQuestEventType.ANIMAL_INTERACTION, "DOG",
                java.util.Set.of("quest.caretaker.animal"), occurredAt)));
    }

    private ReplayMeasurement measureReplay(Fixture fixture) {
        NpcSqlStatementCounter.reset();
        long started = System.nanoTime();
        var result = relationships.replayPending(fixture.user().getId());
        return new ReplayMeasurement(
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                NpcSqlStatementCounter.snapshot(), result);
    }

    private List<Measurement> repeatedNoop(Long worldId) {
        List<Measurement> result = new ArrayList<>();
        for (int run = 0; run < RUNS; run++) {
            result.add(measure(() -> runtime.checkpointWorld(worldId)));
        }
        return result;
    }

    private List<Measurement> repeatedMovement(Long worldId, int movers) {
        List<Measurement> result = new ArrayList<>();
        for (int run = 0; run < RUNS; run++) {
            resetForMovers(worldId, movers);
            var measured = measure(() -> runtime.checkpointWorld(worldId));
            // Collision, player, and protected community-house tiles may make
            // a requested mover ineligible in a particular schedule slot. The
            // checkpoint must still exercise movement without violating those
            // runtime safety rules.
            assertThat(measured.moved()).isBetween(1, movers);
            result.add(measured);
        }
        return result;
    }

    private ConcurrentEvidence concurrentEvidence(Long worldId) throws Exception {
        List<Long> elapsed = new ArrayList<>();
        List<Long> versionDeltas = new ArrayList<>();
        List<NpcSqlStatementCounter.Counts> queries = new ArrayList<>();
        AtomicInteger failures = new AtomicInteger();
        for (int trial = 0; trial < RUNS; trial++) {
            resetAtHomes(worldId);
            long before = versionSum(worldId);
            var ready = new CountDownLatch(5);
            var start = new CountDownLatch(1);
            var done = new CountDownLatch(5);
            NpcSqlStatementCounter.reset();
            long started = System.nanoTime();
            try (var executor = Executors.newFixedThreadPool(5)) {
                for (int caller = 0; caller < 5; caller++) {
                    executor.submit(() -> {
                        ready.countDown();
                        try {
                            start.await(5, TimeUnit.SECONDS);
                            runtime.checkpointWorld(worldId);
                        } catch (Exception exception) {
                            failures.incrementAndGet();
                        } finally {
                            done.countDown();
                        }
                    });
                }
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
            }
            elapsed.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            queries.add(NpcSqlStatementCounter.snapshot());
            versionDeltas.add(versionSum(worldId) - before);
        }
        return new ConcurrentEvidence(elapsed, queries, versionDeltas, failures.get());
    }

    private DialogueEvidence dialogueEvidence(Fixture fixture) {
        var state = ecology.stateForUser(fixture.user().getId());
        var mayor = state.npcPositions().stream()
                .filter(npc -> npc.npcKey().equals(CanonicalNpcKey.NPC_MAYOR.name()))
                .findFirst().orElseThrow();
        transactions().executeWithoutResult(ignored -> {
            WorldPlayerPosition position = positions.findByCharacterId(fixture.character().getId())
                    .orElseGet(() -> positions.save(WorldPlayerPosition.create(
                            fixture.character(), mayor.x(), mayor.y() + 1)));
            position.moveTo(mayor.x(), mayor.y() + 1);
        });

        Holder start = measuredValue(() -> dialogues.start(fixture.user().getId(), mayor.objectId()));
        Holder choice = measuredValue(() -> dialogues.choose(
                fixture.user().getId(), mayor.objectId(), start.session().sessionId(), "village"));
        Holder completion = measuredValue(() -> dialogues.choose(
                fixture.user().getId(), mayor.objectId(), start.session().sessionId(), "finish"));
        assertThat(completion.session().conversationCount()).isEqualTo(1);
        return new DialogueEvidence(start.measurement(), choice.measurement(), completion.measurement());
    }

    private Holder measuredValue(java.util.function.Supplier<com.projecteden.world.npc.DialogueSessionResponse> action) {
        NpcSqlStatementCounter.reset();
        long started = System.nanoTime();
        var response = action.get();
        return new Holder(response, new Measurement(
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                NpcSqlStatementCounter.snapshot(), -1));
    }

    private Measurement measure(java.util.function.Supplier<NpcRuntimeService.CheckpointResult> action) {
        NpcSqlStatementCounter.reset();
        long started = System.nanoTime();
        var result = action.get();
        return new Measurement(
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                NpcSqlStatementCounter.snapshot(), result.movedCount());
    }

    private void resetAtHomes(Long worldId) {
        resetForMovers(worldId, 4);
    }

    private void resetForMovers(Long worldId, int movers) {
        transactions().executeWithoutResult(ignored -> {
            List<NpcRuntimeState> runtimeStates = states.findByWorldIdForUpdate(worldId).stream()
                    .sorted(Comparator.comparing(state -> state.getNpcKey().ordinal()))
                    .toList();
            for (int index = 0; index < runtimeStates.size(); index++) {
                NpcRuntimeState state = runtimeStates.get(index);
                CanonicalNpcKey npcKey = state.getNpcKey();
                var destination = schedules.resolve(
                        npcKey,
                        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
                NpcWorldAnchor alternate = schedules.require(npcKey).slots().stream()
                        .map(slot -> NpcWorldAnchor.valueOf(slot.destination()))
                        .filter(anchor -> anchor.tileX() != destination.destinationX()
                                || anchor.tileY() != destination.destinationY())
                        .findFirst()
                        .orElseThrow();
                int x = index < movers ? alternate.tileX() : destination.destinationX();
                int y = index < movers ? alternate.tileY() : destination.destinationY();
                state.checkpoint(
                        x, y, state.getActivity(), state.getScheduleSlot(),
                        state.getScheduleDateKey(), OLD_CHECKPOINT);
            }
        });
    }

    private long versionSum(Long worldId) {
        return states.findByWorldIdOrderByNpcObjectIdAsc(worldId).stream()
                .mapToLong(NpcRuntimeState::getStateVersion).sum();
    }

    private TransactionTemplate transactions() {
        return new TransactionTemplate(transactionManager);
    }

    private Fixture fixture() {
        return transactions().execute(status -> {
            User user = users.save(new User(
                    "npc-performance@example.com",
                    encoder.encode("password123"),
                    "npc-performance"));
            Character character = characters.save(Character.create(
                    user, "에덴", CharacterGender.NONE, HairStyle.PIXEL_CUT,
                    "brown", Outfit.ROBE, CharacterJob.WIZARD));
            return new Fixture(user, character);
        });
    }

    private static String summary(List<Measurement> values) {
        List<Long> elapsed = values.stream().map(Measurement::elapsedMillis).sorted().toList();
        long min = elapsed.getFirst();
        long median = elapsed.get(elapsed.size() / 2);
        long max = elapsed.getLast();
        long selects = values.stream().mapToLong(value -> value.counts().selects()).sum();
        long updates = values.stream().mapToLong(value -> value.counts().updates()).sum();
        return "{minMs=" + min + ",medianMs=" + median + ",maxMs=" + max
                + ",selects=" + selects + ",updates=" + updates + "}";
    }

    private record Fixture(User user, Character character) { }
    private record Measurement(long elapsedMillis, NpcSqlStatementCounter.Counts counts, int moved) {
        String compact() { return "{ms=" + elapsedMillis + ",queries=" + counts + ",moved=" + moved + "}"; }
    }
    private record Holder(
            com.projecteden.world.npc.DialogueSessionResponse session,
            Measurement measurement) { }
    private record DialogueEvidence(Measurement start, Measurement choice, Measurement completion) { }
    private record ReplayMeasurement(
            long elapsedMillis,
            NpcSqlStatementCounter.Counts counts,
            NpcRelationshipService.ReplayResult result) {
        String compact() { return "{ms=" + elapsedMillis + ",queries=" + counts + ",result=" + result + "}"; }
    }
    private record DeferredEvidence(
            ReplayMeasurement one,
            ReplayMeasurement ten,
            ReplayMeasurement noop,
            long concurrentMillis,
            NpcSqlStatementCounter.Counts concurrentCounts,
            int concurrentFailures,
            long duplicateRows) { }
    private record SchedulerScale(
            int worlds,
            List<Integer> processedPerCycle,
            int remainingDue,
            long duplicateProcessing,
            int failures,
            long elapsedMillis,
            NpcSqlStatementCounter.Counts queries) {
        String compact() {
            return "{worlds=" + worlds
                    + ",cycles=" + processedPerCycle
                    + ",remaining=" + remainingDue
                    + ",duplicates=" + duplicateProcessing
                    + ",failures=" + failures
                    + ",ms=" + elapsedMillis
                    + ",queries=" + queries + "}";
        }
    }
    private record ConcurrentEvidence(
            List<Long> elapsedMillis,
            List<NpcSqlStatementCounter.Counts> counts,
            List<Long> versionDeltaPerTrial,
            int failures) {
        String compact() {
            List<Long> ordered = elapsedMillis.stream().sorted().toList();
            long selects = counts.stream().mapToLong(NpcSqlStatementCounter.Counts::selects).sum();
            long updates = counts.stream().mapToLong(NpcSqlStatementCounter.Counts::updates).sum();
            return "{minMs=" + ordered.getFirst() + ",medianMs=" + ordered.get(ordered.size() / 2)
                    + ",maxMs=" + ordered.getLast() + ",selects=" + selects
                    + ",updates=" + updates + ",versionDeltas=" + versionDeltaPerTrial
                    + ",failures=" + failures + "}";
        }
    }
}
