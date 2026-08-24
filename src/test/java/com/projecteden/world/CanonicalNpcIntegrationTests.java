package com.projecteden.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projecteden.character.domain.Character;
import com.projecteden.character.domain.CharacterGender;
import com.projecteden.character.domain.CharacterJob;
import com.projecteden.character.domain.HairStyle;
import com.projecteden.character.domain.Outfit;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.auth.jwt.JwtTokenProvider;
import com.projecteden.world.chunk.WorldChunkQueryService;
import com.projecteden.world.ecology.MoveRequest;
import com.projecteden.world.ecology.WorldEcologyService;
import com.projecteden.world.ecology.WorldHubLayout;
import com.projecteden.world.ecology.WorldPlayerPosition;
import com.projecteden.world.ecology.WorldPlayerPositionRepository;
import com.projecteden.world.npc.CanonicalNpcKey;
import com.projecteden.world.npc.NpcConversationStateRepository;
import com.projecteden.world.npc.NpcRuntimeService;
import com.projecteden.world.npc.NpcRuntimeStateRepository;
import com.projecteden.world.npc.WorldNpcDialogueService;
import com.projecteden.world.repository.WorldRepository;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CanonicalNpcIntegrationTests {
    @Autowired WorldEcologyService ecology;
    @Autowired NpcRuntimeService runtime;
    @Autowired WorldNpcDialogueService dialogues;
    @Autowired NpcRuntimeStateRepository states;
    @Autowired NpcConversationStateRepository conversations;
    @Autowired WorldPlayerPositionRepository positions;
    @Autowired WorldRepository worlds;
    @Autowired UserRepository users;
    @Autowired CharacterRepository characters;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtTokenProvider tokens;
    @Autowired MockMvc mockMvc;
    @Autowired WorldChunkQueryService chunks;

    @Test
    void preservesCanonicalPlacedObjectIdsAndRepairsExactlyOneRuntimeStatePerNpc() {
        Fixture fixture = fixture("identity");
        var first = ecology.stateForUser(fixture.user().getId());
        var second = ecology.stateForUser(fixture.user().getId());
        var world = worlds.findByCharacterId(fixture.character().getId()).orElseThrow();

        assertThat(first.npcPositions()).hasSize(4);
        assertThat(first.npcPositions()).extracting(npc -> npc.npcKey())
                .containsExactlyInAnyOrder(
                        "NPC_MAYOR", "NPC_GARDENER", "NPC_RESEARCHER", "NPC_CARETAKER");
        assertThat(second.npcPositions()).extracting(npc -> npc.objectId())
                .containsExactlyElementsOf(first.npcPositions().stream().map(npc -> npc.objectId()).toList());
        assertThat(states.findByWorldIdOrderByNpcObjectIdAsc(world.getId())).hasSize(4);
        assertThat(first.npcPositions()).extracting(npc -> npc.id()).doesNotHaveDuplicates();
    }

    @Test
    void readRequestsNeverAdvanceTheCheckpointAndPlayerCannotEnterNpcTile() {
        Fixture fixture = fixture("read-only");
        var first = ecology.stateForUser(fixture.user().getId());
        var world = worlds.findByCharacterId(fixture.character().getId()).orElseThrow();
        Set<Long> versions = states.findByWorldIdOrderByNpcObjectIdAsc(world.getId()).stream()
                .map(state -> state.getStateVersion())
                .collect(java.util.stream.Collectors.toSet());

        ecology.stateForUser(fixture.user().getId());
        ecology.chunkReadContextForUser(fixture.user().getId());

        assertThat(states.findByWorldIdOrderByNpcObjectIdAsc(world.getId()))
                .extracting(state -> state.getStateVersion())
                .allMatch(versions::contains);

        var npc = first.npcPositions().getFirst();
        movePosition(fixture.character(), npc.x(), npc.y() + 1);
        var rejected = ecology.move(fixture.user().getId(), new MoveRequest(npc.x(), npc.y()));
        assertThat(rejected.accepted()).isFalse();
        assertThat(rejected.reason()).isEqualTo("NPC_BLOCKED");
    }

    @Test
    void checkpointIsAtMostOncePerCadenceAndKeepsCanonicalIds() {
        Fixture fixture = fixture("checkpoint");
        var state = ecology.stateForUser(fixture.user().getId());
        var world = worlds.findByCharacterId(fixture.character().getId()).orElseThrow();
        var first = runtime.checkpointWorld(world.getId());
        var versions = states.findByWorldIdOrderByNpcObjectIdAsc(world.getId()).stream()
                .map(runtimeState -> runtimeState.getStateVersion())
                .toList();
        var second = runtime.checkpointWorld(world.getId());

        assertThat(first.npcCount()).isEqualTo(4);
        assertThat(second.movedCount()).isZero();
        assertThat(states.findByWorldIdOrderByNpcObjectIdAsc(world.getId()))
                .extracting(runtimeState -> runtimeState.getStateVersion())
                .containsExactlyElementsOf(versions);
        assertThat(states.findByWorldIdOrderByNpcObjectIdAsc(world.getId()))
                .noneMatch(runtimeState ->
                        (runtimeState.getTileX() == WorldHubLayout.COMMUNITY_HOUSE_ANCHOR_X
                                && runtimeState.getTileY() == WorldHubLayout.COMMUNITY_HOUSE_ANCHOR_Y)
                                || Math.abs(runtimeState.getTileX() - WorldHubLayout.COMMUNITY_HOUSE_APPROACH_X)
                                + Math.abs(runtimeState.getTileY() - WorldHubLayout.COMMUNITY_HOUSE_APPROACH_Y) <= 1);
        assertThat(ecology.stateForUser(fixture.user().getId()).npcPositions())
                .extracting(npc -> npc.objectId())
                .containsExactlyElementsOf(state.npcPositions().stream().map(npc -> npc.objectId()).toList());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void fiveConcurrentCheckpointsSerializeAndAdvanceAtMostOnce() throws Exception {
        Fixture fixture = fixture("checkpoint-concurrent");
        ecology.stateForUser(fixture.user().getId());
        var world = worlds.findByCharacterId(fixture.character().getId()).orElseThrow();
        var oldCheckpoint = LocalDateTime.of(2026, 7, 29, 0, 0);
        var initial = states.findByWorldIdOrderByNpcObjectIdAsc(world.getId());
        initial.forEach(state -> state.checkpoint(
                state.getTileX(), state.getTileY(), state.getActivity(),
                state.getScheduleSlot(), state.getScheduleDateKey(), oldCheckpoint));
        states.saveAllAndFlush(initial);
        long versionBefore = states.findByWorldIdOrderByNpcObjectIdAsc(world.getId()).stream()
                .mapToLong(state -> state.getStateVersion())
                .sum();

        int callers = 5;
        var ready = new CountDownLatch(callers);
        var start = new CountDownLatch(1);
        var completed = new CountDownLatch(callers);
        var failures = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(callers)) {
            for (int index = 0; index < callers; index++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await(5, TimeUnit.SECONDS);
                        runtime.checkpointWorld(world.getId());
                    } catch (Exception exception) {
                        failures.incrementAndGet();
                    } finally {
                        completed.countDown();
                    }
                });
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(completed.await(15, TimeUnit.SECONDS)).isTrue();
        }

        long versionAfter = states.findByWorldIdOrderByNpcObjectIdAsc(world.getId()).stream()
                .mapToLong(state -> state.getStateVersion())
                .sum();
        assertThat(failures.get()).isZero();
        assertThat(versionAfter - versionBefore).isEqualTo(4);
    }

    @Test
    void runsServerValidatedBranchAndPersistsCompletionIdempotently() {
        Fixture fixture = fixture("dialogue");
        var state = ecology.stateForUser(fixture.user().getId());
        var mayor = state.npcPositions().stream()
                .filter(npc -> npc.npcKey().equals(CanonicalNpcKey.NPC_MAYOR.name()))
                .findFirst().orElseThrow();
        movePosition(fixture.character(), mayor.x(), mayor.y() + 1);

        var started = dialogues.start(fixture.user().getId(), mayor.objectId());
        var duplicateStart = dialogues.start(fixture.user().getId(), mayor.objectId());
        var branch = dialogues.choose(
                fixture.user().getId(), mayor.objectId(), started.sessionId(), "village");
        var completed = dialogues.choose(
                fixture.user().getId(), mayor.objectId(), started.sessionId(), "finish");
        var duplicateCompletion = dialogues.choose(
                fixture.user().getId(), mayor.objectId(), started.sessionId(), "finish");

        assertThat(duplicateStart.sessionId()).isEqualTo(started.sessionId());
        assertThat(branch.node().id()).isEqualTo("village");
        assertThat(completed.completed()).isTrue();
        assertThat(duplicateCompletion.conversationCount()).isEqualTo(1);
        assertThat(conversations.findByCharacterIdAndNpcObjectId(
                fixture.character().getId(), mayor.objectId()).orElseThrow().getConversationCount())
                .isEqualTo(1);
    }

    @Test
    void projectsRuntimeNpcOnlyInItsCurrentChunkWithASeparateDynamicVersion() {
        Fixture fixture = fixture("chunk-boundary");
        ecology.stateForUser(fixture.user().getId());
        var world = worlds.findByCharacterId(fixture.character().getId()).orElseThrow();
        var gardener = states.findByWorldIdOrderByNpcObjectIdAsc(world.getId()).stream()
                .filter(state -> state.getNpcKey() == CanonicalNpcKey.NPC_GARDENER)
                .findFirst().orElseThrow();
        gardener.checkpoint(
                8, 8, gardener.getActivity(), "test-boundary",
                "2026-07-29", LocalDateTime.of(2026, 7, 29, 0, 0));
        states.flush();

        var oldChunk = chunks.chunksForUser(fixture.user().getId(), 0, 1, 0).chunks().getFirst();
        var newChunk = chunks.chunksForUser(fixture.user().getId(), 1, 1, 0).chunks().getFirst();

        assertThat(oldChunk.npcs()).noneMatch(npc -> npc.objectId().equals(gardener.getNpcObject().getId()));
        assertThat(newChunk.npcs()).filteredOn(npc -> npc.objectId().equals(gardener.getNpcObject().getId()))
                .singleElement()
                .satisfies(npc -> {
                    assertThat(npc.x()).isEqualTo(8);
                    assertThat(npc.y()).isEqualTo(8);
                });
        assertThat(newChunk.npcStateVersion()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void exposesAuthenticatedDialogueStartAndChoiceApis() throws Exception {
        Fixture fixture = fixture("dialogue-http");
        var state = ecology.stateForUser(fixture.user().getId());
        var npc = state.npcPositions().stream()
                .filter(candidate -> candidate.npcKey().equals("NPC_MAYOR"))
                .findFirst().orElseThrow();
        movePosition(fixture.character(), npc.x(), npc.y() + 1);
        String token = tokens.generateAccessToken(fixture.user());

        String body = mockMvc.perform(post(
                        "/api/worlds/me/npcs/{objectId}/dialogues/start", npc.objectId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.npc.objectId").value(npc.objectId()))
                .andExpect(jsonPath("$.node.id").value("welcome"))
                .andExpect(jsonPath("$.node.choices.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        String sessionId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(body).path("sessionId").asText();

        mockMvc.perform(post(
                        "/api/worlds/me/npcs/{objectId}/dialogues/{sessionId}/choices/{choiceId}",
                        npc.objectId(), sessionId, "activity")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.node.id").value("activity"))
                .andExpect(jsonPath("$.completed").value(false));
    }

    private Fixture fixture(String suffix) {
        User user = users.save(new User(
                "canonical-npc-" + suffix + "@example.com",
                encoder.encode("password123"),
                "npc-" + suffix));
        Character character = characters.save(Character.create(
                user, "에덴", CharacterGender.NONE, HairStyle.PIXEL_CUT,
                "brown", Outfit.ROBE, CharacterJob.WIZARD));
        return new Fixture(user, character);
    }

    private void movePosition(Character character, int x, int y) {
        WorldPlayerPosition position = positions.findByCharacterId(character.getId())
                .orElseGet(() -> positions.save(WorldPlayerPosition.create(character, x, y)));
        position.moveTo(x, y);
    }

    private record Fixture(User user, Character character) { }
}
