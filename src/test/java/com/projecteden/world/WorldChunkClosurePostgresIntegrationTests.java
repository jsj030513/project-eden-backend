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
import com.projecteden.world.chunk.WorldChunk;
import com.projecteden.world.chunk.WorldChunkQueryService;
import com.projecteden.world.chunk.WorldChunkRegionType;
import com.projecteden.world.chunk.WorldChunkRepository;
import com.projecteden.world.chunk.WorldChunkResponse;
import com.projecteden.world.chunk.WorldChunkStatus;
import com.projecteden.world.domain.World;
import com.projecteden.world.ecology.MoveRequest;
import com.projecteden.world.ecology.TerrainTileResponse;
import com.projecteden.world.ecology.WorldCoordinates;
import com.projecteden.world.ecology.WorldEcologyService;
import com.projecteden.world.ecology.WorldPlacedObjectRepository;
import com.projecteden.world.ecology.WorldPlayerPositionRepository;
import com.projecteden.world.ecology.WorldTerrainTileRepository;
import com.projecteden.world.ecology.WorldTerrainBatchWriter;
import com.projecteden.world.ecology.WorldTerrainBatchWriter.TerrainSeed;
import com.projecteden.world.ecology.TerrainType;
import com.projecteden.world.generation.ChunkDiscoveryService;
import com.projecteden.world.generation.ChunkGenerationService;
import com.projecteden.world.generation.RegionTemplate;
import com.projecteden.world.generation.RegionTemplateRegistry;
import com.projecteden.world.generation.RegionTemplateValidator;
import com.projecteden.world.repository.WorldRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
        "spring.flyway.enabled=true"
})
@ActiveProfiles("test")
@Testcontainers
class WorldChunkClosurePostgresIntegrationTests {

    private static final String TEMPLATE_CHECKSUM =
            "f018e797fa340aa29a18a54ce3ce6188ba51eb3c9b5e6b76236b7eb6c0531fed";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("eden_phase3c_closure")
            .withUsername("eden_test")
            .withPassword("eden_test");

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired ChunkGenerationService generation;
    @Autowired ChunkDiscoveryService discovery;
    @Autowired WorldChunkQueryService chunkQuery;
    @Autowired WorldChunkRepository chunks;
    @Autowired WorldTerrainTileRepository terrain;
    @Autowired WorldTerrainBatchWriter terrainWriter;
    @Autowired WorldPlacedObjectRepository objects;
    @Autowired WorldPlayerPositionRepository positions;
    @Autowired WorldEcologyService ecology;
    @Autowired RegionTemplateRegistry templates;
    @Autowired WorldRepository worlds;
    @Autowired UserRepository users;
    @Autowired CharacterRepository characters;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void differentColdWorldsBootstrapInParallelWithoutPartialReadiness() throws Exception {
        List<Fixture> fixtures = java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> coldFixture("parallel-cold-" + index))
                .toList();

        long started = System.nanoTime();
        List<com.projecteden.world.ecology.WorldStateResponse> states = concurrentTasks(fixtures.stream()
                .<Supplier<com.projecteden.world.ecology.WorldStateResponse>>map(fixture ->
                        () -> ecology.stateForUser(fixture.user().getId()))
                .toList());
        long wallMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(states).hasSize(5);
        fixtures.forEach(this::assertCanonicalReadyWorld);
        System.out.println("PHASE4B1_PARALLEL_COLD_WORLDS worlds=5 wallMs=" + wallMillis
                + " terrainPerWorld=384 hubChunksPerWorld=6 npcPerWorld=4 animalsPerWorld=4"
                + " playerPerWorld=1 templateObjectsPerWorld=35 partialWorlds=0 deadlocks=0");
    }

    @Test
    void jdbcBatchIsVisibleToJpaQueriesInTheSameTransactionAndRollsBackAtomically() {
        Fixture visible = coldFixture("batch-visible");
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        List<TerrainSeed> canonical = java.util.stream.IntStream.range(0, 384)
                .mapToObj(index -> new TerrainSeed(index % 24, index / 24, TerrainType.GRASS))
                .toList();

        transactions.executeWithoutResult(status -> {
            assertThat(terrain.findByCharacterIdOrderByYAscXAsc(visible.character().getId())).isEmpty();
            terrainWriter.insertMissing(visible.character().getId(), canonical);
            assertThat(terrain.findByCharacterIdOrderByYAscXAsc(visible.character().getId())).hasSize(384);
        });
        assertThat(terrain.findByCharacterIdOrderByYAscXAsc(visible.character().getId())).hasSize(384);

        Fixture rolledBack = coldFixture("batch-rollback");
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            terrainWriter.insertMissing(rolledBack.character().getId(), canonical);
            assertThat(terrain.findByCharacterIdOrderByYAscXAsc(rolledBack.character().getId())).hasSize(384);
            throw new ForcedGenerationFailure();
        })).isInstanceOf(ForcedGenerationFailure.class);
        assertThat(terrain.findByCharacterIdOrderByYAscXAsc(rolledBack.character().getId())).isEmpty();
    }

    @Test
    void sameColdWorldStateAndChunkHydrationShareOneReadinessLock() throws Exception {
        Fixture fixture = coldFixture("same-world-readiness");

        long started = System.nanoTime();
        List<Object> results = concurrentTasks(List.of(
                () -> ecology.stateForUser(fixture.user().getId()),
                () -> ecology.stateForUser(fixture.user().getId()),
                () -> ecology.stateForUser(fixture.user().getId()),
                () -> ecology.stateForUser(fixture.user().getId()),
                () -> ecology.stateForUser(fixture.user().getId()),
                () -> chunkQuery.chunksForUser(fixture.user().getId(), 1, 1, 0)));
        long wallMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(results).hasSize(6);
        assertCanonicalReadyWorld(fixture);
        assertThat(jdbc.queryForObject("""
                select count(*) from (
                    select tile_x, tile_y from world_terrain_tiles
                    where character_id = ? group by tile_x, tile_y having count(*) > 1
                ) duplicates
                """, Long.class, fixture.character().getId())).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from (
                    select chunk_x, chunk_y from world_chunks
                    where world_id = ? group by chunk_x, chunk_y having count(*) > 1
                ) duplicates
                """, Long.class, fixture.world().getId())).isZero();
        System.out.println("PHASE4B1_SAME_WORLD_READINESS callers=6 state=5 chunks=1 wallMs=" + wallMillis
                + " terrain=384 hubChunks=6 duplicateTerrain=0 duplicateChunks=0 partialWorlds=0 deadlocks=0");
    }

    @Test
    void fiveConcurrentRequestsGenerateOneCanonicalChunkAndRerequestsAreNoOps() throws Exception {
        Fixture fixture = fixture("five-way");
        int chunkX = 3;
        int chunkY = 0;

        long coldStarted = System.nanoTime();
        List<WorldChunkResponse> concurrent = concurrent(5,
                () -> chunkQuery.chunksForUser(fixture.user().getId(), chunkX, chunkY, 0)
                        .chunks().getFirst());
        long coldWallMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - coldStarted);

        assertThat(concurrent).hasSize(5);
        assertThat(concurrent).extracting(WorldChunkResponse::version)
                .containsOnly(concurrent.getFirst().version());
        assertThat(concurrent).extracting(WorldChunkResponse::regionType)
                .containsOnly(WorldChunkRegionType.FOREST);
        assertThat(concurrent).extracting(WorldChunkResponse::templateKey).containsOnly("FOREST_V3");
        assertThat(concurrent).extracting(WorldChunkResponse::generationVersion).containsOnly(3);
        assertThat(concurrent).extracting(WorldChunkResponse::status)
                .containsOnly(WorldChunkStatus.GENERATED);
        assertCanonicalChunk(fixture, chunkX, chunkY);

        WorldChunk stored = chunk(fixture.world().getId(), chunkX, chunkY);
        LocalDateTime generatedAt = stored.getGeneratedAt();
        String version = concurrent.getFirst().version();
        long terrainBefore = terrainCount(fixture, chunkX, chunkY);
        long warmStarted = System.nanoTime();
        List<WorldChunkResponse> repeated = concurrent(5,
                () -> chunkQuery.chunksForUser(fixture.user().getId(), chunkX, chunkY, 0)
                        .chunks().getFirst());
        long warmWallMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - warmStarted);

        assertThat(repeated).extracting(WorldChunkResponse::version).containsOnly(version);
        assertThat(terrainCount(fixture, chunkX, chunkY)).isEqualTo(terrainBefore);
        assertThat(chunk(fixture.world().getId(), chunkX, chunkY).getGeneratedAt())
                .isEqualTo(generatedAt);
        assertCanonicalChunk(fixture, chunkX, chunkY);
        System.out.println("PHASE3C_FIVE_REQUEST_TIMING coldWallMs=" + coldWallMillis
                + " warmWallMs=" + warmWallMillis + " requests=5 deadlocks=0");
    }

    @Test
    void adjacentGenerationIsIndependentOfRequestOrderAndConcurrency() throws Exception {
        Fixture aThenB = fixture("order-a-b");
        WorldChunkResponse abA = response(aThenB, -1, 0);
        WorldChunkResponse abB = response(aThenB, -1, 1);

        Fixture bThenA = fixture("order-b-a");
        WorldChunkResponse baB = response(bThenA, -1, 1);
        WorldChunkResponse baA = response(bThenA, -1, 0);

        Fixture simultaneous = fixture("order-concurrent");
        List<WorldChunkResponse> concurrent = concurrentTasks(List.of(
                () -> response(simultaneous, -1, 0),
                () -> response(simultaneous, -1, 1)));
        WorldChunkResponse concurrentA = concurrent.stream()
                .filter(chunk -> chunk.chunkY() == 0).findFirst().orElseThrow();
        WorldChunkResponse concurrentB = concurrent.stream()
                .filter(chunk -> chunk.chunkY() == 1).findFirst().orElseThrow();

        assertEquivalent(abA, baA, concurrentA);
        assertEquivalent(abB, baB, concurrentB);
        assertThat(southEdge(abA)).containsExactlyElementsOf(northEdge(abB));
        assertThat(southEdge(baA)).containsExactlyElementsOf(northEdge(baB));
        assertThat(southEdge(concurrentA)).containsExactlyElementsOf(northEdge(concurrentB));
    }

    @Test
    void generationRollsBackAtomicallyAndASecondRequestRetriesCleanly() {
        Fixture fixture = fixture("rollback");
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            generation.ensureGenerated(fixture.world().getId(), 3, 1);
            throw new ForcedGenerationFailure();
        })).isInstanceOf(ForcedGenerationFailure.class);

        assertThat(findChunks(fixture.world().getId(), 3, 1)).isEmpty();
        assertThat(terrainCount(fixture, 3, 1)).isZero();
        assertThat(objectCount(fixture, 3, 1)).isZero();

        WorldChunkResponse retry = response(fixture, 3, 1);
        assertThat(retry.status()).isEqualTo(WorldChunkStatus.GENERATED);
        assertCanonicalChunk(fixture, 3, 1);
    }

    @Test
    void repairsFailedAndPartialRowsIdempotentlyWithoutLosingDiscovery() {
        Fixture failedTen = fixture("repair-failed-ten");
        response(failedTen, -1, 0);
        retainTerrain(failedTen, -1, 0, 10);
        jdbc.update("""
                update world_chunks set status = 'FAILED'
                where world_id = ? and chunk_x = -1 and chunk_y = 0
                """, failedTen.world().getId());
        WorldChunkResponse repairedA = response(failedTen, -1, 0);
        assertThat(repairedA.terrain()).hasSize(64);
        assertCanonicalChunk(failedTen, -1, 0);

        Fixture generatedSixtyThree = fixture("repair-63");
        response(generatedSixtyThree, 3, 0);
        discovery.discover(generatedSixtyThree.world().getId(), 3, 0);
        LocalDateTime discoveryTimestamp = chunk(generatedSixtyThree.world().getId(), 3, 0)
                .getDiscoveredAt();
        retainTerrain(generatedSixtyThree, 3, 0, 63);
        WorldChunkResponse repairedB = response(generatedSixtyThree, 3, 0);
        LocalDateTime repairedAt = chunk(generatedSixtyThree.world().getId(), 3, 0)
                .getGeneratedAt();
        WorldChunkResponse repairedAgain = response(generatedSixtyThree, 3, 0);
        assertThat(repairedB.terrain()).hasSize(64);
        assertThat(repairedAgain.version()).isEqualTo(repairedB.version());
        assertThat(chunk(generatedSixtyThree.world().getId(), 3, 0).getGeneratedAt())
                .isEqualTo(repairedAt);
        assertThat(chunk(generatedSixtyThree.world().getId(), 3, 0).getDiscoveredAt())
                .isEqualTo(discoveryTimestamp);

        Fixture metadataMissing = fixture("repair-metadata");
        response(metadataMissing, 1, 2);
        retainTerrain(metadataMissing, 1, 2, 10);
        jdbc.update("""
                delete from world_chunks
                where world_id = ? and chunk_x = 1 and chunk_y = 2
                """, metadataMissing.world().getId());
        WorldChunkResponse repairedD = response(metadataMissing, 1, 2);
        assertThat(repairedD.terrain()).hasSize(64);
        assertCanonicalChunk(metadataMissing, 1, 2);

        assertThat(templates.require(WorldChunkRegionType.MEADOW).requiredObjects()).isEmpty();
        assertThat(templates.require(WorldChunkRegionType.FOREST).requiredObjects()).isEmpty();
        assertThat(templates.require(WorldChunkRegionType.POND).requiredObjects()).isEmpty();
    }

    @Test
    void concurrentFirstDiscoverySetsOneTimestampExactlyOnce() throws Exception {
        Fixture fixture = fixture("discover-five");
        response(fixture, -1, 0);

        List<ChunkDiscoveryService.DiscoveryResult> first = concurrent(5,
                () -> discovery.discover(fixture.world().getId(), -1, 0));
        LocalDateTime timestamp = chunk(fixture.world().getId(), -1, 0).getDiscoveredAt();

        assertThat(timestamp).isNotNull();
        assertThat(first).filteredOn(ChunkDiscoveryService.DiscoveryResult::newlyDiscovered)
                .hasSize(1);
        assertThat(first).filteredOn(result -> !result.newlyDiscovered()).hasSize(4);
        assertThat(first).extracting(result -> result.chunk().getDiscoveredAt())
                .containsOnly(timestamp);

        List<ChunkDiscoveryService.DiscoveryResult> repeated = concurrent(5,
                () -> discovery.discover(fixture.world().getId(), -1, 0));
        assertThat(repeated).noneMatch(ChunkDiscoveryService.DiscoveryResult::newlyDiscovered);
        assertThat(chunk(fixture.world().getId(), -1, 0).getDiscoveredAt()).isEqualTo(timestamp);

        Fixture other = fixture("discover-isolated");
        response(other, -1, 0);
        assertThat(chunk(other.world().getId(), -1, 0).getDiscoveredAt()).isNull();
    }

    @Test
    void allOuterChunksAreCompleteAndEveryWalkableCellConnectsToEveryEdge() {
        Fixture fixture = fixture("connectivity");
        var all = chunkQuery.chunksForUser(fixture.user().getId(), 1, 1, 2);
        List<WorldChunkResponse> outer = all.chunks().stream()
                .filter(chunk -> chunk.regionType() != WorldChunkRegionType.HUB)
                .toList();

        assertThat(all.chunks()).hasSize(20);
        assertThat(outer).hasSize(14);
        assertThat(outer).allSatisfy(chunk -> {
            assertThat(chunk.terrain()).hasSize(64);
            assertConnectedWalkableTerrain(chunk);
        });
        Map<String, TerrainTileResponse> worldTerrain = all.chunks().stream()
                .flatMap(chunk -> chunk.terrain().stream())
                .collect(Collectors.toMap(tile -> tile.x() + ":" + tile.y(), tile -> tile));
        assertWorldReachable(worldTerrain, List.of("-1:7", "24:7", "11:16"));
    }

    @Test
    void recordsGenerationQueryAndTimingEvidenceWithoutHardThresholds() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        List<String> report = new ArrayList<>();
        for (RegionTarget target : List.of(
                new RegionTarget("MEADOW", -1, 0),
                new RegionTarget("FOREST", 3, 0),
                new RegionTarget("POND", 1, 2))) {
            List<Long> cold = new ArrayList<>();
            List<Long> warm = new ArrayList<>();
            for (int run = 0; run < 5; run++) {
                Fixture fixture = fixture("perf-" + target.name() + "-" + run);
                statistics.clear();
                cold.add(timedMillis(() -> generation.ensureGenerated(
                        fixture.world().getId(), target.chunkX(), target.chunkY())));
                long prepares = statistics.getPrepareStatementCount();
                long inserts = statistics.getEntityInsertCount();
                long transactions = statistics.getSuccessfulTransactionCount();
                statistics.clear();
                warm.add(timedMicros(() -> generation.ensureGenerated(
                        fixture.world().getId(), target.chunkX(), target.chunkY())));
                report.add(target.name() + " run=" + run
                        + " prepares=" + prepares + " inserts=" + inserts
                        + " transactions=" + transactions);
            }
            report.add(target.name() + " coldMs=" + distribution(cold)
                    + " warmMicros=" + distribution(warm));
        }

        Fixture queryFixture = fixture("query-evidence");
        statistics.clear();
        chunkQuery.chunksForUser(queryFixture.user().getId(), -1, 0, 0);
        long radiusZeroQueries = statistics.getPrepareStatementCount();
        statistics.clear();
        chunkQuery.chunksForUser(queryFixture.user().getId(), 1, 1, 1);
        long radiusOneQueries = statistics.getPrepareStatementCount();
        statistics.clear();
        chunkQuery.chunksForUser(queryFixture.user().getId(), -1, 0, 0);
        long warmRadiusZeroQueries = statistics.getPrepareStatementCount();
        statistics.clear();
        chunkQuery.chunksForUser(queryFixture.user().getId(), 1, 1, 1);
        long warmRadiusOneQueries = statistics.getPrepareStatementCount();
        statistics.clear();
        positions.findByCharacterId(queryFixture.character().getId()).orElseThrow().moveTo(0, 7);
        ecology.move(queryFixture.user().getId(), new MoveRequest(-1, 7));
        long discoveryQueries = statistics.getPrepareStatementCount();

        report.add("radius0Prepares=" + radiusZeroQueries);
        report.add("radius1Prepares=" + radiusOneQueries);
        report.add("warmRadius0Prepares=" + warmRadiusZeroQueries);
        report.add("warmRadius1Prepares=" + warmRadiusOneQueries);
        report.add("discoveryMovePrepares=" + discoveryQueries);
        report.add("templateChecksum=" + templates.checksum());
        System.out.println("PHASE3C_CLOSURE_PERFORMANCE " + String.join(" | ", report));

        assertThat(templates.checksum()).isEqualTo(TEMPLATE_CHECKSUM);
        assertThat(radiusZeroQueries).isPositive();
        assertThat(radiusOneQueries).isPositive();
        assertThat(discoveryQueries).isPositive();
    }

    private Fixture fixture(String suffix) {
        Fixture fixture = coldFixture(suffix);
        ecology.stateForUser(fixture.user().getId());
        return fixture;
    }

    private Fixture coldFixture(String suffix) {
        String unique = suffix + "-" + UUID.randomUUID().toString().substring(0, 8);
        User user = users.save(new User(
                "phase3c-" + unique + "@example.com",
                passwordEncoder.encode("password123"),
                "phase3c-" + unique));
        Character character = characters.save(Character.create(
                user, "청크", CharacterGender.NONE, HairStyle.PIXEL_CUT,
                "brown", Outfit.ROBE, CharacterJob.WIZARD));
        World world = worlds.saveAndFlush(World.create(character, character.getId()));
        return new Fixture(user, character, world);
    }

    private void assertCanonicalReadyWorld(Fixture fixture) {
        assertThat(terrain.findByCharacterIdOrderByYAscXAsc(fixture.character().getId())).hasSize(384);
        assertThat(chunks.findByWorldIdOrderByChunkYAscChunkXAsc(fixture.world().getId())).hasSize(6);
        assertThat(objects.findByCharacterIdOrderByIdAsc(fixture.character().getId())).hasSize(35);
        assertThat(objects.findByCharacterIdOrderByIdAsc(fixture.character().getId()).stream()
                .filter(object -> object.getAssetType() == com.projecteden.world.ecology.WorldAssetType.DEFAULT_DOG
                        || object.getAssetType() == com.projecteden.world.ecology.WorldAssetType.DEFAULT_CAT
                        || object.getAssetType() == com.projecteden.world.ecology.WorldAssetType.DEFAULT_BIRD))
                .hasSize(4);
        assertThat(positions.findByCharacterId(fixture.character().getId())).isPresent();
        assertThat(jdbc.queryForObject(
                "select count(*) from npc_runtime_states where world_id = ?",
                Long.class, fixture.world().getId())).isEqualTo(4L);
        assertThat(worlds.findById(fixture.world().getId()).orElseThrow().getVillageTemplateVersion())
                .isEqualTo(3);
    }

    private WorldChunkResponse response(Fixture fixture, int chunkX, int chunkY) {
        return chunkQuery.chunksForUser(fixture.user().getId(), chunkX, chunkY, 0)
                .chunks().getFirst();
    }

    private WorldChunk chunk(Long worldId, int chunkX, int chunkY) {
        return chunks.findByWorldIdAndChunkXAndChunkY(worldId, chunkX, chunkY).orElseThrow();
    }

    private List<WorldChunk> findChunks(Long worldId, int chunkX, int chunkY) {
        return chunks.findByWorldIdOrderByChunkYAscChunkXAsc(worldId).stream()
                .filter(chunk -> chunk.getChunkX() == chunkX && chunk.getChunkY() == chunkY)
                .toList();
    }

    private long terrainCount(Fixture fixture, int chunkX, int chunkY) {
        return terrain.countByCharacterIdAndXBetweenAndYBetween(
                fixture.character().getId(),
                WorldCoordinates.chunkMinTile(chunkX),
                WorldCoordinates.chunkMaxTile(chunkX),
                WorldCoordinates.chunkMinTile(chunkY),
                WorldCoordinates.chunkMaxTile(chunkY));
    }

    private long objectCount(Fixture fixture, int chunkX, int chunkY) {
        int minX = WorldCoordinates.chunkMinTile(chunkX) * WorldCoordinates.TILE_SIZE;
        int maxX = (WorldCoordinates.chunkMaxTile(chunkX) + 1) * WorldCoordinates.TILE_SIZE - 1;
        int minY = WorldCoordinates.chunkMinTile(chunkY) * WorldCoordinates.TILE_SIZE;
        int maxY = (WorldCoordinates.chunkMaxTile(chunkY) + 1) * WorldCoordinates.TILE_SIZE - 1;
        return objects.findByCharacterIdAndPixelRangeOrderByIdAsc(
                fixture.character().getId(), minX, maxX, minY, maxY).size();
    }

    private void retainTerrain(Fixture fixture, int chunkX, int chunkY, int retained) {
        List<Long> ids = jdbc.queryForList("""
                select id from world_terrain_tiles
                where character_id = ?
                  and tile_x between ? and ?
                  and tile_y between ? and ?
                order by tile_y, tile_x
                """, Long.class,
                fixture.character().getId(),
                WorldCoordinates.chunkMinTile(chunkX),
                WorldCoordinates.chunkMaxTile(chunkX),
                WorldCoordinates.chunkMinTile(chunkY),
                WorldCoordinates.chunkMaxTile(chunkY));
        ids.stream().skip(retained)
                .forEach(id -> jdbc.update("delete from world_terrain_tiles where id = ?", id));
    }

    private void assertCanonicalChunk(Fixture fixture, int chunkX, int chunkY) {
        List<WorldChunk> matching = findChunks(fixture.world().getId(), chunkX, chunkY);
        assertThat(matching).hasSize(1);
        assertThat(matching.getFirst().getStatus()).isEqualTo(WorldChunkStatus.GENERATED);
        assertThat(matching.getFirst().getGenerationVersion()).isEqualTo(3);
        assertThat(terrainCount(fixture, chunkX, chunkY)).isEqualTo(64);
        List<String> coordinates = terrain.findByCharacterIdAndXBetweenAndYBetweenOrderByYAscXAsc(
                        fixture.character().getId(),
                        WorldCoordinates.chunkMinTile(chunkX),
                        WorldCoordinates.chunkMaxTile(chunkX),
                        WorldCoordinates.chunkMinTile(chunkY),
                        WorldCoordinates.chunkMaxTile(chunkY)).stream()
                .map(tile -> tile.getX() + ":" + tile.getY()).toList();
        assertThat(coordinates).doesNotHaveDuplicates();
        assertThat(objectCount(fixture, chunkX, chunkY)).isZero();
    }

    private static void assertEquivalent(
            WorldChunkResponse first,
            WorldChunkResponse second,
            WorldChunkResponse third) {
        assertThat(List.of(second, third)).allSatisfy(candidate -> {
            assertThat(candidate.regionType()).isEqualTo(first.regionType());
            assertThat(candidate.templateKey()).isEqualTo(first.templateKey());
            assertThat(candidate.version()).isEqualTo(first.version());
            assertThat(candidate.terrain()).containsExactlyElementsOf(first.terrain());
            assertThat(candidate.decorations()).containsExactlyElementsOf(first.decorations());
        });
    }

    private static List<com.projecteden.world.ecology.TerrainType> northEdge(WorldChunkResponse chunk) {
        int y = WorldCoordinates.chunkMinTile(chunk.chunkY());
        return chunk.terrain().stream().filter(tile -> tile.y() == y)
                .sorted(Comparator.comparingInt(TerrainTileResponse::x))
                .map(TerrainTileResponse::terrainType).toList();
    }

    private static List<com.projecteden.world.ecology.TerrainType> southEdge(WorldChunkResponse chunk) {
        int y = WorldCoordinates.chunkMaxTile(chunk.chunkY());
        return chunk.terrain().stream().filter(tile -> tile.y() == y)
                .sorted(Comparator.comparingInt(TerrainTileResponse::x))
                .map(TerrainTileResponse::terrainType).toList();
    }

    private static void assertConnectedWalkableTerrain(WorldChunkResponse chunk) {
        Set<String> walkable = chunk.terrain().stream().filter(TerrainTileResponse::walkable)
                .map(tile -> tile.x() + ":" + tile.y())
                .collect(Collectors.toCollection(HashSet::new));
        assertThat(walkable).isNotEmpty();
        Set<String> visited = flood(walkable.iterator().next(), walkable);
        assertThat(visited).containsExactlyInAnyOrderElementsOf(walkable);
        int minX = WorldCoordinates.chunkMinTile(chunk.chunkX());
        int maxX = WorldCoordinates.chunkMaxTile(chunk.chunkX());
        int minY = WorldCoordinates.chunkMinTile(chunk.chunkY());
        int maxY = WorldCoordinates.chunkMaxTile(chunk.chunkY());
        assertThat(visited).contains(
                (minX + 3) + ":" + minY,
                maxX + ":" + (minY + 7),
                (minX + 3) + ":" + maxY,
                minX + ":" + (minY + 7));
    }

    private static void assertWorldReachable(
            Map<String, TerrainTileResponse> terrain,
            List<String> guaranteedEntrances) {
        Set<String> walkable = terrain.values().stream().filter(TerrainTileResponse::walkable)
                .map(tile -> tile.x() + ":" + tile.y())
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> visited = flood("11:7", walkable);
        assertThat(visited).containsAll(guaranteedEntrances);
    }

    private static Set<String> flood(String start, Set<String> walkable) {
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        assertThat(walkable).contains(start);
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            String[] parts = current.split(":");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            for (int[] delta : List.of(
                    new int[]{1, 0}, new int[]{-1, 0},
                    new int[]{0, 1}, new int[]{0, -1})) {
                String next = (x + delta[0]) + ":" + (y + delta[1]);
                if (walkable.contains(next) && visited.add(next)) queue.add(next);
            }
        }
        return visited;
    }

    private static <T> List<T> concurrent(int count, Supplier<T> task) throws Exception {
        List<Supplier<T>> tasks = java.util.stream.IntStream.range(0, count)
                .mapToObj(ignored -> task).toList();
        return concurrentTasks(tasks);
    }

    private static <T> List<T> concurrentTasks(List<Supplier<T>> tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(tasks.size())) {
            List<Future<T>> futures = tasks.stream().map(task -> executor.submit(() -> {
                ready.countDown();
                start.await();
                return task.get();
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) results.add(future.get());
            return results;
        }
    }

    private static long timedMillis(Runnable action) {
        long started = System.nanoTime();
        action.run();
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    private static long timedMicros(Runnable action) {
        long started = System.nanoTime();
        action.run();
        return (System.nanoTime() - started) / 1_000;
    }

    private static String distribution(List<Long> values) {
        List<Long> ordered = values.stream().sorted().toList();
        return "min=" + ordered.getFirst()
                + ",median=" + ordered.get(ordered.size() / 2)
                + ",max=" + ordered.getLast();
    }

    private record Fixture(User user, Character character, World world) {
    }

    private record RegionTarget(String name, int chunkX, int chunkY) {
    }

    private static final class ForcedGenerationFailure extends RuntimeException {
    }
}
