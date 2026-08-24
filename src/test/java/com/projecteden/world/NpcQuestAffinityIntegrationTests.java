package com.projecteden.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projecteden.character.domain.Character;
import com.projecteden.character.domain.CharacterGender;
import com.projecteden.character.domain.CharacterJob;
import com.projecteden.character.domain.HairStyle;
import com.projecteden.character.domain.Outfit;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.ecology.WorldEcologyService;
import com.projecteden.world.ecology.WorldPlayerPosition;
import com.projecteden.world.ecology.WorldPlayerPositionRepository;
import com.projecteden.world.npc.AffinityLevel;
import com.projecteden.world.npc.CanonicalNpcKey;
import com.projecteden.world.npc.NpcAffinityEventRepository;
import com.projecteden.world.npc.NpcAffinityStateRepository;
import com.projecteden.world.npc.NpcQuestEventRepository;
import com.projecteden.world.npc.NpcQuestEventProcessingStatus;
import com.projecteden.world.npc.NpcQuestEvent;
import com.projecteden.world.npc.NpcQuestEventType;
import com.projecteden.world.npc.NpcQuestRegistry;
import com.projecteden.world.npc.NpcQuestStateRepository;
import com.projecteden.world.npc.NpcQuestStatus;
import com.projecteden.world.npc.NpcRelationshipService;
import com.projecteden.world.npc.NpcRuntimeService;
import com.projecteden.world.npc.NpcRuntimeState;
import com.projecteden.world.npc.NpcRuntimeStateRepository;
import com.projecteden.world.npc.WorldNpcDialogueService;
import com.projecteden.world.repository.WorldRepository;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NpcQuestAffinityIntegrationTests {
    @Autowired WorldEcologyService ecology;
    @Autowired NpcRuntimeService runtime;
    @Autowired WorldNpcDialogueService dialogues;
    @Autowired NpcRelationshipService relationships;
    @Autowired NpcRuntimeStateRepository runtimeStates;
    @Autowired NpcAffinityStateRepository affinities;
    @Autowired NpcAffinityEventRepository affinityEvents;
    @Autowired NpcQuestStateRepository questStates;
    @Autowired NpcQuestEventRepository questEvents;
    @Autowired NpcQuestRegistry questRegistry;
    @Autowired WorldPlayerPositionRepository positions;
    @Autowired WorldRepository worlds;
    @Autowired UserRepository users;
    @Autowired CharacterRepository characters;
    @Autowired PasswordEncoder encoder;

    @Test
    void appliesFirstDailyRepeatedAndRapidDialogueAffinityRules() {
        Fixture fixture = fixture("rules");
        NpcRuntimeState mayor = npc(fixture, CanonicalNpcKey.NPC_MAYOR);
        LocalDateTime first = LocalDateTime.of(2026, 7, 29, 10, 0);

        var firstResult = relationships.completeDialogue(
                fixture.character(), mayor, "session-1", "dialogue.mayor.default", "finish", first);
        var duplicate = relationships.completeDialogue(
                fixture.character(), mayor, "session-1", "dialogue.mayor.default", "finish", first);
        var rapid = relationships.completeDialogue(
                fixture.character(), mayor, "session-2", "dialogue.mayor.default", "finish",
                first.plusMinutes(1));
        var repeated = relationships.completeDialogue(
                fixture.character(), mayor, "session-3", "dialogue.mayor.default", "finish",
                first.plusMinutes(10));
        var nextDay = relationships.completeDialogue(
                fixture.character(), mayor, "session-4", "dialogue.mayor.default", "finish",
                first.plusDays(1));

        assertThat(firstResult.relationship().currentAffinity()).isEqualTo(70);
        assertThat(duplicate.notifications()).isEmpty();
        assertThat(rapid.relationship().currentAffinity()).isEqualTo(70);
        assertThat(repeated.relationship().currentAffinity()).isEqualTo(73);
        assertThat(nextDay.relationship().currentAffinity()).isEqualTo(86);
        assertThat(affinityEvents.countByCharacterId(fixture.character().getId())).isEqualTo(4);
    }

    @Test
    void unlocksProgressesAndCompletesPrerequisiteQuestServerSide() {
        Fixture fixture = fixture("unlock");
        NpcRuntimeState mayor = npc(fixture, CanonicalNpcKey.NPC_MAYOR);
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 10, 0);

        relationships.completeDialogue(
                fixture.character(), mayor, "unlock-1", "dialogue.mayor.default", "finish", now);
        relationships.completeDialogue(
                fixture.character(), mayor, "unlock-2", "dialogue.other", "finish", now.plusMinutes(10));
        relationships.completeDialogue(
                fixture.character(), mayor, "unlock-3", "dialogue.another", "finish", now.plusMinutes(20));
        relationships.completeDialogue(
                fixture.character(), mayor, "unlock-4", "dialogue.final", "finish", now.plusMinutes(30));

        var relationship = relationships.relationship(fixture.user().getId(), mayor.getNpcObject().getId());
        assertThat(relationship.currentAffinity()).isEqualTo(100);
        assertThat(relationship.level()).isEqualTo(AffinityLevel.ACQUAINTANCE);
        assertThat(relationship.quests())
                .filteredOn(quest -> quest.questId().equals("quest.mayor.community"))
                .singleElement()
                .extracting(quest -> quest.status())
                .isEqualTo(NpcQuestStatus.AVAILABLE);

        var notifications = relationships.recordEvent(
                fixture.user().getId(),
                "community-visit-1",
                NpcQuestEventType.COMMUNITY_VISIT,
                "COMMUNITY_HOUSE");
        assertThat(notifications).anyMatch(notification ->
                notification.type().equals("QUEST_COMPLETED"));
        assertThat(questStates.findByCharacterIdAndQuestId(
                fixture.character().getId(), "quest.mayor.community").orElseThrow().getStatus())
                .isEqualTo(NpcQuestStatus.COMPLETED);
    }

    @Test
    void supportsRepeatableAnimalQuestWithoutDuplicateEventProgress() {
        Fixture fixture = fixture("repeatable");
        NpcRuntimeState caretaker = npc(fixture, CanonicalNpcKey.NPC_CARETAKER);

        relationships.recordEvent(
                fixture.user().getId(), "animal-1",
                NpcQuestEventType.ANIMAL_INTERACTION, "DOG");
        relationships.recordEvent(
                fixture.user().getId(), "animal-1",
                NpcQuestEventType.ANIMAL_INTERACTION, "DOG");
        relationships.recordEvent(
                fixture.user().getId(), "animal-2",
                NpcQuestEventType.ANIMAL_INTERACTION, "CAT");

        var relationship = relationships.relationship(
                fixture.user().getId(), caretaker.getNpcObject().getId());
        assertThat(relationship.questCompletedCount()).isEqualTo(2);
        assertThat(relationship.currentAffinity()).isEqualTo(60);
        assertThat(questEvents.countByCharacterId(fixture.character().getId())).isEqualTo(2);
    }

    @Test
    void hidesLockedHiddenQuestAndPreservesStateAcrossReadsAndDialogueApi() {
        Fixture fixture = fixture("persistence");
        NpcRuntimeState mayor = npc(fixture, CanonicalNpcKey.NPC_MAYOR);
        movePosition(fixture.character(), mayor.getTileX(), mayor.getTileY() + 1);

        var start = dialogues.start(fixture.user().getId(), mayor.getNpcObject().getId());
        dialogues.choose(
                fixture.user().getId(), mayor.getNpcObject().getId(), start.sessionId(), "village");
        var completed = dialogues.choose(
                fixture.user().getId(), mayor.getNpcObject().getId(), start.sessionId(), "finish");
        var afterReload = relationships.relationships(fixture.user().getId());

        assertThat(completed.relationship().currentAffinity()).isEqualTo(70);
        assertThat(completed.notifications()).isNotEmpty();
        assertThat(afterReload).hasSize(4);
        assertThat(afterReload).flatExtracting(relationship -> relationship.quests())
                .noneMatch(quest -> quest.questId().equals("quest.researcher.visit"));
        assertThat(affinities.findAllForCharacter(fixture.character().getId())).hasSize(4);
    }

    @Test
    void rejectsRelationshipLookupForAnotherUsersCanonicalObject() {
        Fixture owner = fixture("owner");
        Fixture attacker = fixture("attacker");
        NpcRuntimeState ownerMayor = npc(owner, CanonicalNpcKey.NPC_MAYOR);
        npc(attacker, CanonicalNpcKey.NPC_MAYOR);

        assertThatThrownBy(() -> relationships.relationship(
                attacker.user().getId(), ownerMayor.getNpcObject().getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("NPC_NOT_OWNED");
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void serializesConcurrentDuplicateQuestEvents() throws Exception {
        Fixture fixture = fixture("concurrent");
        npc(fixture, CanonicalNpcKey.NPC_CARETAKER);
        int callers = 5;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(callers);
        AtomicInteger failures = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(callers)) {
            for (int index = 0; index < callers; index++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await(5, TimeUnit.SECONDS);
                        relationships.recordEvent(
                                fixture.user().getId(),
                                "same-animal-event",
                                NpcQuestEventType.ANIMAL_INTERACTION,
                                "DOG");
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

        assertThat(failures.get()).isZero();
        assertThat(questEvents.countByCharacterId(fixture.character().getId())).isEqualTo(1);
        assertThat(questStates.findByCharacterIdAndQuestId(
                fixture.character().getId(), "quest.caretaker.animal").orElseThrow().getProgress())
                .isEqualTo(1);
    }

    @Test
    void validatesRegistryCoverageAndAffinityThresholdContract() {
        assertThat(questRegistry.all()).hasSize(6);
        assertThat(questRegistry.all())
                .allMatch(quest -> quest.id() != null
                        && quest.title() != null
                        && quest.requirements().targetCount() > 0
                        && quest.rewards().affinity() >= 0);
        assertThat(questRegistry.all())
                .extracting(quest -> quest.requirements().eventType())
                .containsExactlyInAnyOrder(
                        "TALK", "COMMUNITY_VISIT", "INSPECT",
                        "TAKE_PHOTO", "VISIT_LOCATION", "ANIMAL_INTERACTION");
        assertThat(AffinityLevel.from(0)).isEqualTo(AffinityLevel.STRANGER);
        assertThat(AffinityLevel.from(100)).isEqualTo(AffinityLevel.ACQUAINTANCE);
        assertThat(AffinityLevel.from(200)).isEqualTo(AffinityLevel.FRIEND);
        assertThat(AffinityLevel.from(400)).isEqualTo(AffinityLevel.CLOSE_FRIEND);
        assertThat(AffinityLevel.from(700)).isEqualTo(AffinityLevel.BEST_FRIEND);
        assertThat(AffinityLevel.from(1000)).isEqualTo(AffinityLevel.BEST_FRIEND);
    }

    @Test
    void capsRepeatableQuestAffinityAtOneThousand() {
        Fixture fixture = fixture("cap");
        NpcRuntimeState caretaker = npc(fixture, CanonicalNpcKey.NPC_CARETAKER);
        for (int index = 0; index < 40; index++) {
            relationships.recordEvent(
                    fixture.user().getId(),
                    "animal-cap-" + index,
                    NpcQuestEventType.ANIMAL_INTERACTION,
                    "DOG");
        }

        var relationship = relationships.relationship(
                fixture.user().getId(), caretaker.getNpcObject().getId());
        assertThat(relationship.currentAffinity()).isEqualTo(1000);
        assertThat(relationship.maxAffinity()).isEqualTo(1000);
        assertThat(relationship.level()).isEqualTo(AffinityLevel.BEST_FRIEND);
    }

    @Test
    void progressesInspectPhotoAndHiddenVisitEventsThroughTheQuestService() {
        Fixture fixture = fixture("events");
        NpcRuntimeState gardener = npc(fixture, CanonicalNpcKey.NPC_GARDENER);
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(10);

        relationships.recordEvent(
                fixture.user().getId(), "inspect-farm",
                NpcQuestEventType.INSPECT, "FARM");
        for (int index = 0; index < 5; index++) {
            relationships.completeDialogue(
                    fixture.character(), gardener, "gardener-session-" + index,
                    "dialogue.gardener." + index, "finish", now.plusMinutes(index * 10L));
        }
        relationships.recordEvent(
                fixture.user().getId(), "photo-1",
                NpcQuestEventType.TAKE_PHOTO, null);
        relationships.recordEvent(
                fixture.user().getId(), "outer-visit-1",
                NpcQuestEventType.VISIT_LOCATION, "OUTER_REGION");

        var gardenerRelationship = relationships.relationship(
                fixture.user().getId(), gardener.getNpcObject().getId());
        assertThat(gardenerRelationship.quests())
                .filteredOn(quest -> quest.questId().equals("quest.gardener.inspect"))
                .singleElement().extracting(quest -> quest.status())
                .isEqualTo(NpcQuestStatus.COMPLETED);
        assertThat(gardenerRelationship.quests())
                .filteredOn(quest -> quest.questId().equals("quest.gardener.photo"))
                .singleElement().extracting(quest -> quest.status())
                .isEqualTo(NpcQuestStatus.COMPLETED);
        assertThat(relationships.relationships(fixture.user().getId()))
                .flatExtracting(relationship -> relationship.quests())
                .filteredOn(quest -> quest.questId().equals("quest.researcher.visit"))
                .singleElement()
                .extracting(quest -> quest.status())
                .isEqualTo(NpcQuestStatus.COMPLETED);
    }

    @Test
    void acceptsPhotoEventBeforeCanonicalNpcRuntimeExistsWithoutBreakingRecognition() {
        Fixture fixture = fixture("pre-runtime-photo");
        worlds.save(World.create(fixture.character(), 4102L));

        relationships.recordEvent(
                fixture.user().getId(), "photo-before-runtime",
                NpcQuestEventType.TAKE_PHOTO, null);

        assertThat(questEvents.countByCharacterId(fixture.character().getId())).isEqualTo(1);
        assertThat(questEvents.findByCharacterIdOrderByCreatedAtAscIdAsc(fixture.character().getId()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getProcessingStatus()).isEqualTo(NpcQuestEventProcessingStatus.IGNORED);
                    assertThat(event.getOutcomeReason()).isEqualTo("NO_ACTIVE_QUEST_AT_OCCURRENCE");
                });
        assertThat(questStates.findByCharacterIdOrderByQuestIdAsc(fixture.character().getId())).isEmpty();
        assertThat(affinities.findAllForCharacter(fixture.character().getId())).isEmpty();
    }

    @Test
    void replaysActivePhotoQuestAfterCanonicalRuntimeBootstrapExactlyOnce() {
        Fixture fixture = fixture("deferred-photo");
        World world = worlds.save(World.create(fixture.character(), 4103L));
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 29, 11, 0);
        var active = com.projecteden.world.npc.NpcQuestState.create(
                fixture.character(), "quest.gardener.photo", NpcQuestStatus.AVAILABLE, occurredAt.minusMinutes(1));
        active.activate(occurredAt.minusMinutes(1));
        questStates.save(active);

        relationships.recordEvent(
                fixture.user().getId(), "photo-deferred-1",
                NpcQuestEventType.TAKE_PHOTO, null);

        NpcQuestEvent pending = questEvents.findByCharacterIdOrderByCreatedAtAscIdAsc(
                fixture.character().getId()).getFirst();
        assertThat(pending.getWorld().getId()).isEqualTo(world.getId());
        assertThat(pending.getProcessingStatus()).isEqualTo(NpcQuestEventProcessingStatus.PENDING);
        assertThat(pending.eligibleQuestIds()).containsExactly("quest.gardener.photo");

        ecology.stateForUser(fixture.user().getId());
        int affinityAfterReplay = relationships.relationships(fixture.user().getId()).stream()
                .filter(value -> value.npcKey().equals(CanonicalNpcKey.NPC_GARDENER.name()))
                .findFirst().orElseThrow().currentAffinity();
        relationships.replayPending(fixture.user().getId());

        NpcQuestEvent processed = questEvents.findByCharacterIdOrderByCreatedAtAscIdAsc(
                fixture.character().getId()).getFirst();
        assertThat(processed.getProcessingStatus()).isEqualTo(NpcQuestEventProcessingStatus.PROCESSED);
        assertThat(processed.getOutcomeReason()).isEqualTo("REPLAY_APPLIED");
        assertThat(processed.getProcessedAt()).isNotNull();
        assertThat(processed.getProcessingAttempts()).isEqualTo(1);
        assertThat(questStates.findByCharacterIdAndQuestId(
                fixture.character().getId(), "quest.gardener.photo").orElseThrow().getStatus())
                .isEqualTo(NpcQuestStatus.COMPLETED);
        assertThat(relationships.relationships(fixture.user().getId()).stream()
                .filter(value -> value.npcKey().equals(CanonicalNpcKey.NPC_GARDENER.name()))
                .findFirst().orElseThrow().currentAffinity()).isEqualTo(affinityAfterReplay);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void serializesFiveConcurrentReplaysAndLeavesTerminalEventImmutable() throws Exception {
        Fixture fixture = fixture("replay-concurrent");
        NpcRuntimeState caretaker = npc(fixture, CanonicalNpcKey.NPC_CARETAKER);
        World world = worlds.findByCharacterId(fixture.character().getId()).orElseThrow();
        var state = questStates.findByCharacterIdAndQuestId(
                fixture.character().getId(), "quest.caretaker.animal").orElseThrow();
        state.activate(LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(1));
        questStates.save(state);
        questEvents.save(NpcQuestEvent.create(
                fixture.character(), world, "deferred-animal-concurrent",
                NpcQuestEventType.ANIMAL_INTERACTION, "DOG",
                java.util.Set.of("quest.caretaker.animal"),
                LocalDateTime.now(java.time.ZoneOffset.UTC)));

        int callers = 5;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(callers);
        AtomicInteger failures = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(callers)) {
            for (int index = 0; index < callers; index++) {
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

        assertThat(failures.get()).isZero();
        NpcQuestEvent terminal = questEvents.findByCharacterIdOrderByCreatedAtAscIdAsc(
                fixture.character().getId()).getFirst();
        assertThat(terminal.getProcessingStatus()).isEqualTo(NpcQuestEventProcessingStatus.PROCESSED);
        assertThat(terminal.getProcessingAttempts()).isEqualTo(1);
        assertThat(questStates.findByCharacterIdAndQuestId(
                fixture.character().getId(), "quest.caretaker.animal").orElseThrow().getProgress())
                .isEqualTo(1);
        assertThat(affinities.findByCharacterIdAndNpcObjectId(
                fixture.character().getId(), caretaker.getNpcObject().getId()).orElseThrow()
                .getQuestCompletedCount()).isEqualTo(1);
    }

    @Test
    void retriesFailedDeferredEventAndThenBecomesCanonicalNoOp() {
        Fixture fixture = fixture("replay-retry");
        npc(fixture, CanonicalNpcKey.NPC_CARETAKER);
        World world = worlds.findByCharacterId(fixture.character().getId()).orElseThrow();
        var state = questStates.findByCharacterIdAndQuestId(
                fixture.character().getId(), "quest.caretaker.animal").orElseThrow();
        state.activate(LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(1));
        questStates.save(state);
        NpcQuestEvent failed = NpcQuestEvent.create(
                fixture.character(), world, "deferred-animal-retry",
                NpcQuestEventType.ANIMAL_INTERACTION, "DOG",
                java.util.Set.of("quest.caretaker.animal"),
                LocalDateTime.now(java.time.ZoneOffset.UTC));
        failed.failed(LocalDateTime.now(java.time.ZoneOffset.UTC), "INJECTED_FAILURE");
        questEvents.save(failed);

        var firstRetry = relationships.replayPending(fixture.user().getId());
        var secondRetry = relationships.replayPending(fixture.user().getId());

        assertThat(firstRetry.processed()).isEqualTo(1);
        assertThat(secondRetry.selected()).isZero();
        assertThat(questEvents.findByCharacterIdOrderByCreatedAtAscIdAsc(fixture.character().getId()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getProcessingStatus()).isEqualTo(NpcQuestEventProcessingStatus.PROCESSED);
                    assertThat(event.getProcessingAttempts()).isEqualTo(2);
                });
    }

    private NpcRuntimeState npc(Fixture fixture, CanonicalNpcKey key) {
        ecology.stateForUser(fixture.user().getId());
        var world = worlds.findByCharacterId(fixture.character().getId()).orElseThrow();
        return runtimeStates.findByWorldIdOrderByNpcObjectIdAsc(world.getId()).stream()
                .filter(state -> state.getNpcKey() == key)
                .findFirst().orElseThrow();
    }

    private Fixture fixture(String suffix) {
        User user = users.save(new User(
                "npc-affinity-" + suffix + "@example.com",
                encoder.encode("password123"),
                "affinity-" + suffix));
        Character character = characters.save(Character.create(
                user, "에덴", CharacterGender.NONE, HairStyle.PIXEL_CUT,
                "brown", Outfit.ROBE, CharacterJob.WIZARD));
        return new Fixture(user, character);
    }

    private void movePosition(Character character, int x, int y) {
        WorldPlayerPosition position = positions.findByCharacterId(character.getId())
                .orElseGet(() -> WorldPlayerPosition.create(character, x, y));
        position.moveTo(x, y);
        positions.save(position);
    }

    private record Fixture(User user, Character character) { }
}
