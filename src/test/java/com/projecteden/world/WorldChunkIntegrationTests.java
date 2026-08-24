package com.projecteden.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projecteden.auth.jwt.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecteden.character.domain.Character;
import com.projecteden.character.domain.CharacterGender;
import com.projecteden.character.domain.CharacterJob;
import com.projecteden.character.domain.HairStyle;
import com.projecteden.character.domain.Outfit;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.world.chunk.WorldChunkQueryService;
import com.projecteden.world.chunk.WorldChunkRepository;
import com.projecteden.world.chunk.WorldChunkRegionType;
import com.projecteden.world.domain.World;
import com.projecteden.world.ecology.MoveRequest;
import com.projecteden.world.ecology.WorldEcologyService;
import com.projecteden.world.ecology.WorldPlayerPosition;
import com.projecteden.world.ecology.WorldPlayerPositionRepository;
import com.projecteden.world.ecology.WorldTerrainTileRepository;
import com.projecteden.world.ecology.TerrainType;
import com.projecteden.world.ecology.WorldPlacedObjectRepository;
import com.projecteden.world.repository.WorldRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WorldChunkIntegrationTests {

    @Autowired WorldChunkQueryService chunkQuery;
    @Autowired WorldChunkRepository chunks;
    @Autowired WorldEcologyService ecology;
    @Autowired WorldPlayerPositionRepository positions;
    @Autowired WorldTerrainTileRepository terrain;
    @Autowired WorldPlacedObjectRepository objects;
    @Autowired UserRepository users;
    @Autowired CharacterRepository characters;
    @Autowired WorldRepository worlds;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenProvider tokens;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void bootstrapsExactlySixHubChunksAndRecoversMissingMetadata() {
        Fixture fixture = fixture("bootstrap");

        ecology.stateForUser(fixture.user().getId());
        assertThat(chunks.findByWorldIdOrderByChunkYAscChunkXAsc(fixture.world().getId()))
                .hasSize(6)
                .extracting(chunk -> chunk.getChunkX() + ":" + chunk.getChunkY())
                .containsExactly("0:0", "1:0", "2:0", "0:1", "1:1", "2:1");

        var missing = chunks.findByWorldIdAndChunkXAndChunkY(fixture.world().getId(), 2, 1).orElseThrow();
        chunks.delete(missing);
        chunks.flush();

        ecology.stateForUser(fixture.user().getId());
        assertThat(chunks.findByWorldIdOrderByChunkYAscChunkXAsc(fixture.world().getId())).hasSize(6);
    }

    @Test
    void returnsRadiusZeroWithStableGroupingAndNoDuplicateEntityIds() {
        Fixture fixture = fixture("radius-zero");

        var first = chunkQuery.chunksForUser(fixture.user().getId(), 1, 1, 0);
        var second = chunkQuery.chunksForUser(fixture.user().getId(), 1, 1, 0);

        assertThat(first.chunks()).hasSize(1);
        assertThat(first.chunks().getFirst().chunkX()).isEqualTo(1);
        assertThat(first.chunks().getFirst().chunkY()).isEqualTo(1);
        assertThat(first.chunks().getFirst().terrain()).hasSize(64);
        assertThat(first.chunks().getFirst().version()).isEqualTo(second.chunks().getFirst().version());
        assertThat(first.chunks().getFirst().terrain())
                .extracting(tile -> tile.x() + ":" + tile.y()).doesNotHaveDuplicates();
        assertThat(first.chunks().getFirst().placedObjects())
                .extracting(object -> object.id()).doesNotHaveDuplicates();
    }

    @Test
    void lazilyGeneratesOuterChunksAndIsolatesOwnership() {
        Fixture first = fixture("first-owner");
        Fixture second = fixture("second-owner");

        var radiusOne = chunkQuery.chunksForUser(first.user().getId(), 1, 1, 1);
        var radiusTwo = chunkQuery.chunksForUser(first.user().getId(), 1, 1, 2);
        var other = chunkQuery.chunksForUser(second.user().getId(), 1, 1, 2);

        assertThat(radiusOne.chunks()).hasSize(9);
        assertThat(radiusTwo.chunks()).hasSize(20);
        var firstIds = radiusTwo.chunks().stream().flatMap(chunk -> chunk.placedObjects().stream())
                .map(object -> object.id()).collect(java.util.stream.Collectors.toSet());
        var otherIds = other.chunks().stream().flatMap(chunk -> chunk.placedObjects().stream())
                .map(object -> object.id()).collect(java.util.stream.Collectors.toSet());
        assertThat(firstIds).isNotEmpty();
        assertThat(firstIds).doesNotContainAnyElementsOf(otherIds);
    }

    @Test
    void serializesChunkContractAndRejectsInvalidRadius() throws Exception {
        Fixture fixture = fixture("http");
        String token = tokens.generateAccessToken(fixture.user());

        mockMvc.perform(get("/api/worlds/me/chunks")
                        .queryParam("centerChunkX", "1")
                        .queryParam("centerChunkY", "1")
                        .queryParam("radius", "0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.world.chunkSize").value(8))
                .andExpect(jsonPath("$.world.tileSize").value(48))
                .andExpect(jsonPath("$.chunks.length()").value(1))
                .andExpect(jsonPath("$.chunks[0].terrain.length()").value(64))
                .andExpect(jsonPath("$.chunks[0].version").isNotEmpty())
                .andExpect(jsonPath("$.availableInteractions").isArray());

        mockMvc.perform(get("/api/worlds/me/chunks")
                        .queryParam("centerChunkX", "1")
                        .queryParam("centerChunkY", "1")
                        .queryParam("radius", "3")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void movesAcrossEveryHubChunkBoundaryWithoutDependingOnClientCache() {
        Fixture fixture = fixture("movement");
        ecology.stateForUser(fixture.user().getId());
        WorldPlayerPosition position = positions.findByCharacterId(fixture.character().getId()).orElseThrow();

        position.moveTo(7, 7);
        assertThat(ecology.move(fixture.user().getId(), new MoveRequest(8, 7)).accepted()).isTrue();
        position.moveTo(15, 7);
        assertThat(ecology.move(fixture.user().getId(), new MoveRequest(16, 7)).accepted()).isTrue();
        position.moveTo(8, 7);
        assertThat(ecology.move(fixture.user().getId(), new MoveRequest(8, 8)).accepted()).isTrue();
    }

    @Test
    void changesContentVersionWhenTerrainChanges() {
        Fixture fixture = fixture("content-version");
        var before = chunkQuery.chunksForUser(fixture.user().getId(), 0, 0, 0).chunks().getFirst();
        var tile = terrain.findByCharacterIdAndXAndY(fixture.character().getId(), 0, 0).orElseThrow();
        tile.changeTerrain(TerrainType.GRASS);
        terrain.flush();

        var after = chunkQuery.chunksForUser(fixture.user().getId(), 0, 0, 0).chunks().getFirst();
        assertThat(after.version()).isNotEqualTo(before.version());
    }

    @Test
    void stateThenChunkPreloadMovesAnimalsAtMostOncePerCheckpoint() {
        Fixture fixture = fixture("animal-checkpoint");
        ecology.stateForUser(fixture.user().getId());
        fixture.world().markAnimalMovement(LocalDateTime.now().minusSeconds(31));
        worlds.flush();

        ecology.stateForUser(fixture.user().getId());
        Map<Long, String> afterState = animalCoordinates(fixture.character().getId());
        chunkQuery.chunksForUser(fixture.user().getId(), 1, 1, 1);
        Map<Long, String> afterChunks = animalCoordinates(fixture.character().getId());

        assertThat(afterChunks).isEqualTo(afterState);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void serializesSimultaneousChunkQueriesThroughTheWorldCheckpointLock() throws Exception {
        Fixture fixture = fixture("concurrent-query");
        fixture.world().markAnimalMovement(LocalDateTime.now().minusSeconds(31));
        worlds.saveAndFlush(fixture.world());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> chunkQuery.chunksForUser(fixture.user().getId(), 1, 1, 1));
            var second = executor.submit(() -> chunkQuery.chunksForUser(fixture.user().getId(), 1, 1, 1));

            assertThat(first.get().chunks()).hasSize(9);
            assertThat(second.get().chunks()).hasSize(9);
            assertThat(first.get().chunks()).extracting(chunk -> chunk.version())
                    .containsExactlyElementsOf(second.get().chunks().stream()
                            .map(chunk -> chunk.version()).toList());
        }
    }

    @Test
    void recordsBoundedPayloadEvidenceForEverySupportedRadius() throws Exception {
        Fixture fixture = fixture("payload");
        var radiusZero = chunkQuery.chunksForUser(fixture.user().getId(), 1, 1, 0);
        var radiusOne = chunkQuery.chunksForUser(fixture.user().getId(), 1, 1, 1);
        var radiusTwo = chunkQuery.chunksForUser(fixture.user().getId(), 1, 1, 2);
        int stateBytes = objectMapper.writeValueAsBytes(ecology.stateForUser(fixture.user().getId())).length;
        int zeroBytes = objectMapper.writeValueAsBytes(radiusZero).length;
        int oneBytes = objectMapper.writeValueAsBytes(radiusOne).length;
        int twoBytes = objectMapper.writeValueAsBytes(radiusTwo).length;

        assertThat(radiusZero.chunks()).hasSize(1);
        assertThat(radiusOne.chunks()).hasSize(9);
        assertThat(radiusTwo.chunks()).hasSize(20);
        assertThat(zeroBytes).isLessThan(stateBytes);
        assertThat(zeroBytes).isLessThan(oneBytes);
        assertThat(twoBytes).isGreaterThan(oneBytes);
        System.out.printf(
                "PHASE3B_PAYLOAD_EVIDENCE state=%d radius0=%d radius1=%d radius2=%d rows=%d/%d/%d%n",
                stateBytes, zeroBytes, oneBytes, twoBytes,
                radiusZero.chunks().stream().mapToInt(chunk -> chunk.terrain().size()).sum(),
                radiusOne.chunks().stream().mapToInt(chunk -> chunk.terrain().size()).sum(),
                radiusTwo.chunks().stream().mapToInt(chunk -> chunk.terrain().size()).sum());
    }

    @Test
    void deterministicallyGeneratesGuaranteedOuterRegionsWithCompleteTerrain() {
        Fixture fixture = fixture("outer-regions");

        var meadow = chunkQuery.chunksForUser(fixture.user().getId(), -1, 0, 0).chunks().getFirst();
        var forest = chunkQuery.chunksForUser(fixture.user().getId(), 3, 0, 0).chunks().getFirst();
        var pond = chunkQuery.chunksForUser(fixture.user().getId(), 1, 2, 0).chunks().getFirst();
        var pondAgain = chunkQuery.chunksForUser(fixture.user().getId(), 1, 2, 0).chunks().getFirst();

        assertThat(meadow.regionType()).isEqualTo(WorldChunkRegionType.MEADOW);
        assertThat(forest.regionType()).isEqualTo(WorldChunkRegionType.FOREST);
        assertThat(pond.regionType()).isEqualTo(WorldChunkRegionType.POND);
        assertThat(meadow.terrain()).hasSize(64);
        assertThat(forest.terrain()).hasSize(64);
        assertThat(pond.terrain()).hasSize(64);
        assertThat(pond.terrain()).anyMatch(tile -> tile.terrainType() == TerrainType.WATER);
        assertThat(pond.terrain()).anyMatch(tile -> tile.terrainType() == TerrainType.BRIDGE);
        assertThat(pond.version()).isEqualTo(pondAgain.version());
        assertThat(pond.terrain()).containsExactlyElementsOf(pondAgain.terrain());
    }

    @Test
    void firstOuterEntryDiscoversChunkOnceAndPreservesDiscoveryTimestamp() {
        Fixture fixture = fixture("outer-discovery");
        ecology.stateForUser(fixture.user().getId());
        chunkQuery.chunksForUser(fixture.user().getId(), -1, 0, 0);
        WorldPlayerPosition position = positions.findByCharacterId(fixture.character().getId()).orElseThrow();
        position.moveTo(0, 7);
        positions.flush();

        var first = ecology.move(fixture.user().getId(), new MoveRequest(-1, 7));
        var discoveredAt = chunks.findByWorldIdAndChunkXAndChunkY(fixture.world().getId(), -1, 0)
                .orElseThrow().getDiscoveredAt();
        position.moveTo(0, 7);
        positions.flush();
        var second = ecology.move(fixture.user().getId(), new MoveRequest(-1, 7));

        assertThat(first.accepted()).isTrue();
        assertThat(first.enteredChunk()).isTrue();
        assertThat(first.newlyDiscovered()).isTrue();
        assertThat(first.regionType()).isEqualTo(WorldChunkRegionType.MEADOW);
        assertThat(discoveredAt).isNotNull();
        assertThat(second.accepted()).isTrue();
        assertThat(second.newlyDiscovered()).isFalse();
        assertThat(chunks.findByWorldIdAndChunkXAndChunkY(fixture.world().getId(), -1, 0)
                .orElseThrow().getDiscoveredAt()).isEqualTo(discoveredAt);
    }

    private Fixture fixture(String suffix) {
        User user = users.save(new User(
                "chunk-" + suffix + "@example.com",
                passwordEncoder.encode("password123"),
                "chunk-" + suffix));
        Character character = characters.save(Character.create(
                user, "청크", CharacterGender.NONE, HairStyle.PIXEL_CUT,
                "brown", Outfit.ROBE, CharacterJob.WIZARD));
        World world = worlds.saveAndFlush(World.create(character, character.getId()));
        return new Fixture(user, character, world);
    }

    private record Fixture(User user, Character character, World world) {
    }

    private Map<Long, String> animalCoordinates(Long characterId) {
        return objects.findByCharacterIdOrderByIdAsc(characterId).stream()
                .filter(object -> object.getAssetType().name().startsWith("DEFAULT_")
                        && !object.getAssetType().name().startsWith("DEFAULT_NPC_"))
                .collect(java.util.stream.Collectors.toMap(
                        object -> object.getId(),
                        object -> object.getPositionX() + ":" + object.getPositionY()));
    }
}
