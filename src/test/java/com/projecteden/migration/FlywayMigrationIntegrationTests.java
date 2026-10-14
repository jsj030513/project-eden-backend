package com.projecteden.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projecteden.ProjectEdenBackendApplication;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Repository-managed lineages: fresh PostgreSQL or an existing B9 canonical database,
 * followed by forward migrations. Arbitrary pre-B9 databases are outside this contract;
 * the retained V1-V7 migrations do not bootstrap their historical core schema.
 */
@Testcontainers
class FlywayMigrationIntegrationTests {

    // Golden metadata from the checksum-verified preserved canonical dump.
    // Do not derive the expected checksum from the current migration resource.
    private static final String CANONICAL_B9_VERSION = "9";
    private static final String CANONICAL_B9_TYPE = "SQL_BASELINE";
    private static final String CANONICAL_B9_SCRIPT = "B9__project_eden_schema_baseline.sql";
    private static final int CANONICAL_B9_CHECKSUM = -567082747;
    private static final List<String> FORWARD_SCRIPTS = List.of(
            "V10__add_world_animal_movement_checkpoint.sql",
            "V11__add_finite_world_bounds.sql",
            "V12__add_world_chunks.sql",
            "V13__align_bridge_and_community_house_footprints.sql",
            "V14__add_deterministic_outer_regions.sql",
            "V15__align_outer_region_connectors.sql");

    // Non-static: each test receives a new, empty PostgreSQL container.
    @Container
    private final PostgreSQLContainer<?> postgres = newPostgres();

    private static PostgreSQLContainer<?> newPostgres() {
        return new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("project_eden_migration")
                .withUsername("eden_migration")
                .withPassword("migration-test-only");
    }

    private static Flyway flyway(PostgreSQLContainer<?> database, String location) {
        return Flyway.configure()
                .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .locations(location)
                .baselineOnMigrate(false)
                .load();
    }

    @Test
    void emptyPostgresqlMigratesToLatestAndSpringValidatesSchema() throws SQLException {
        Flyway latest = flyway(postgres, "classpath:db/migration");
        assertThat(applicationTableCount()).isZero();
        MigrateResult first = latest.migrate();
        assertThat(first.success).isTrue();
        assertThat(first.migrationsExecuted).isEqualTo(7);
        assertThat(first.targetSchemaVersion).isEqualTo("15");
        latest.validate();
        printHistory("empty-db");
        assertV15History();
        assertRequiredTablesAndIndexes();
        assertFoundationSchema();
        assertIdempotent(latest);
        assertSpringValidatesSchema();
    }

    @Test
    void existingB9PostgresqlUpgradesToLatestWithSchemaParityAndJpaValidation(@TempDir Path baselineDirectory)
            throws Exception {
        assertThat(applicationTableCount()).isZero();
        copyBaseline(baselineDirectory);
        Flyway baseline = flyway(postgres, "filesystem:" + baselineDirectory.toAbsolutePath());
        MigrateResult initial = baseline.migrate();
        assertThat(initial.success).isTrue();
        assertThat(initial.migrationsExecuted).isEqualTo(1);
        assertThat(initial.targetSchemaVersion).isEqualTo(CANONICAL_B9_VERSION);
        assertCanonicalB9History();
        assertThat(queryForStrings(postgres, """
                select version || '|' || type || '|' || script || '|' || success
                from flyway_schema_history order by installed_rank
                """)).containsExactly(CANONICAL_B9_VERSION + "|" + CANONICAL_B9_TYPE + "|" + CANONICAL_B9_SCRIPT + "|true");
        printHistory("b9-initial");
        assertThat(applicationTableCount()).isEqualTo(44);
        assertRequiredTablesAndIndexes();
        assertThat(queryForInt("""
                select count(*) from information_schema.columns
                where table_schema = 'public' and table_name = 'worlds'
                  and column_name in ('last_animal_movement_at', 'min_tile_x', 'max_tile_x',
                                      'min_tile_y', 'max_tile_y', 'world_generation_version')
                """)).isZero();
        assertThat(queryForInt("""
                select count(*) from information_schema.tables
                where table_schema = 'public' and table_name = 'world_chunks'
                """)).isZero();
        seedExistingB9Worlds();

        // The same database now sees the full immutable migration set.
        Flyway latest = flyway(postgres, "classpath:db/migration");
        for (var migration : latest.info().all()) {
            System.out.printf("UPGRADE_INFO %s | %s | %s | %s%n", migration.getVersion(),
                    migration.getType(), migration.getScript(), migration.getState());
        }
        assertThat(latest.info().applied()).extracting(migration -> migration.getScript())
                .containsExactly(CANONICAL_B9_SCRIPT);
        assertThat(latest.info().pending()).extracting(migration -> migration.getScript())
                .containsExactlyElementsOf(FORWARD_SCRIPTS);
        // Standalone validate() rejects pending scripts by default. Permit only that expected
        // state here after the exact pending-set gate. Flyway validates applied V10+
        // integrity; the explicit canonical assertion protects B9, which Flyway 11.7.2
        // does not checksum-validate at the applied baseline version.
        Flyway.configure().configuration(latest.getConfiguration())
                .ignoreMigrationPatterns("*:pending").load().validate();
        MigrateResult upgrade = latest.migrate();
        assertThat(upgrade.success).isTrue();
        assertThat(upgrade.migrationsExecuted).isEqualTo(6);
        assertThat(upgrade.targetSchemaVersion).isEqualTo("15");
        latest.validate();
        printHistory("b9-upgrade");
        assertV15History();
        assertRequiredTablesAndIndexes();
        assertFoundationSchema();
        assertPersistedWorldAlignment();
        assertIdempotent(latest);
        assertPersistedWorldAlignment();

        // Independent fresh-to-latest database; no shared test order or cached fingerprint.
        try (PostgreSQLContainer<?> fresh = newPostgres()) {
            fresh.start();
            MigrateResult freshResult = flyway(fresh, "classpath:db/migration").migrate();
            assertThat(freshResult.success).isTrue();
            assertThat(freshResult.migrationsExecuted).isEqualTo(7);
            assertThat(freshResult.targetSchemaVersion).isEqualTo("15");
            List<String> emptySchema = applicationSchema(fresh);
            List<String> upgradedSchema = applicationSchema(postgres);
            assertThat(upgradedSchema).as("all application tables, columns, constraints and indexes")
                    .containsExactlyElementsOf(emptySchema);
            System.out.printf("SCHEMA_PARITY entries=%d empty_sha256=%s upgraded_sha256=%s%n",
                    emptySchema.size(), sha256(String.join("\n", emptySchema).getBytes(StandardCharsets.UTF_8)),
                    sha256(String.join("\n", upgradedSchema).getBytes(StandardCharsets.UTF_8)));
        }
        assertSpringValidatesSchema();
    }

    @Test
    void canonicalB9GuardRejectsInvalidHistoryMetadata() {
        B9HistoryRow canonical = new B9HistoryRow("9", "SQL_BASELINE",
                "B9__project_eden_schema_baseline.sql", -567082747, true);
        assertCanonicalB9History(List.of(canonical));
        List<B9HistoryRow> invalidRows = List.of(
                new B9HistoryRow("8", canonical.type(), canonical.script(), canonical.checksum(), true),
                new B9HistoryRow(canonical.version(), "SQL", canonical.script(), canonical.checksum(), true),
                new B9HistoryRow(canonical.version(), canonical.type(), "V9__wrong.sql", canonical.checksum(), true),
                new B9HistoryRow(canonical.version(), canonical.type(), canonical.script(), -567082746, true),
                new B9HistoryRow(canonical.version(), canonical.type(), canonical.script(), canonical.checksum(), false),
                new B9HistoryRow(canonical.version(), canonical.type(), canonical.script(), null, true));
        for (B9HistoryRow invalid : invalidRows) {
            assertThatThrownBy(() -> assertCanonicalB9History(List.of(invalid)))
                    .as("reject invalid canonical metadata: %s", invalid)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("canonical B9 history");
        }
        assertThatThrownBy(() -> assertCanonicalB9History(List.of()))
                .isInstanceOf(AssertionError.class).hasMessageContaining("canonical B9 history");
        assertThatThrownBy(() -> assertCanonicalB9History(List.of(canonical, canonical)))
                .isInstanceOf(AssertionError.class).hasMessageContaining("canonical B9 history");
    }

    private void assertCanonicalB9History() throws SQLException {
        List<B9HistoryRow> history = new ArrayList<>();
        try (Connection connection = postgres.createConnection("");
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("""
                        select version, type, script, checksum, success
                        from flyway_schema_history
                        where version = '%s' or type = '%s' or script = '%s'
                        order by installed_rank
                        """.formatted(CANONICAL_B9_VERSION, CANONICAL_B9_TYPE, CANONICAL_B9_SCRIPT))) {
            while (rows.next()) {
                history.add(new B9HistoryRow(rows.getString(1), rows.getString(2), rows.getString(3),
                        rows.getObject(4, Integer.class), rows.getObject(5, Boolean.class)));
            }
        }
        assertCanonicalB9History(history);
    }

    private static void assertCanonicalB9History(List<B9HistoryRow> history) {
        assertThat(history).as("canonical B9 history").containsExactly(new B9HistoryRow(
                CANONICAL_B9_VERSION, CANONICAL_B9_TYPE, CANONICAL_B9_SCRIPT, CANONICAL_B9_CHECKSUM, true));
    }

    private record B9HistoryRow(String version, String type, String script, Integer checksum, Boolean success) {
    }

    private void copyBaseline(Path directory) throws Exception {
        byte[] source;
        try (InputStream resource = getClass().getResourceAsStream("/db/migration/" + CANONICAL_B9_SCRIPT)) {
            assertThat(resource).isNotNull();
            source = resource.readAllBytes();
        }
        Path copy = directory.resolve(CANONICAL_B9_SCRIPT);
        Files.write(copy, source);
        byte[] copied = Files.readAllBytes(copy);
        assertThat(copied).isEqualTo(source);
        assertThat(copied).isEqualTo(Files.readAllBytes(Path.of("src/main/resources/db/migration", CANONICAL_B9_SCRIPT)));
        try (var files = Files.list(directory)) {
            assertThat(files.map(file -> file.getFileName().toString()).toList()).containsExactly(CANONICAL_B9_SCRIPT);
        }
        System.out.printf("B9_FIXTURE_SHA256 %s | %s%n", CANONICAL_B9_SCRIPT, sha256(copied));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private void assertV15History() throws SQLException {
        assertCanonicalB9History();
        List<String> scripts = new ArrayList<>();
        scripts.add(CANONICAL_B9_SCRIPT);
        scripts.addAll(FORWARD_SCRIPTS);
        assertThat(queryForStrings(postgres, "select script from flyway_schema_history order by installed_rank"))
                .containsExactlyElementsOf(scripts);
        assertThat(queryForString("select string_agg(version, ',' order by installed_rank) from flyway_schema_history"))
                .isEqualTo("9,10,11,12,13,14,15");
        assertThat(queryForString("select type from flyway_schema_history where version = '9'"))
                .isEqualTo("SQL_BASELINE");
        assertThat(queryForInt("select count(*) from flyway_schema_history where not success"))
                .isZero();
        assertThat(queryForInt("select count(*) from flyway_schema_history where version::int >= 10 and type = 'SQL'"))
                .isEqualTo(6);
    }

    private void assertIdempotent(Flyway latest) {
        MigrateResult second = latest.migrate();
        assertThat(second.success).isTrue();
        assertThat(second.migrationsExecuted).isZero();
        assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("15");
    }

    private void printHistory(String path) throws SQLException {
        System.out.println("MIGRATION_DATABASE " + path + " " + postgres.getJdbcUrl()
                + " PostgreSQL " + queryForString("show server_version"));
        try (Connection connection = postgres.createConnection("");
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("""
                        select installed_rank, version, type, script, checksum, success, description
                        from flyway_schema_history order by installed_rank
                        """)) {
            while (rows.next()) {
                System.out.printf("MIGRATION_HISTORY %s %d | %s | %s | %s | %s | %s | %s%n",
                        path, rows.getInt(1), rows.getString(2), rows.getString(3),
                        rows.getString(4), rows.getString(5), rows.getBoolean(6), rows.getString(7));
            }
        }
    }

    private void seedExistingB9Worlds() throws SQLException {
        // Data only, in the schema created by the exact B9 migration. No historical DDL.
        try (Connection connection = postgres.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert into users (id, email, nickname, password, provider, role, status)
                    values (9001, 'migration@example.invalid', 'migration', 'test-only', 'LOCAL', 'USER', 'ACTIVE');
                    insert into characters (id, energy, exp, gender, hair_color, hair_style, job,
                                            level, name, outfit, weapon_type, user_id)
                    values (9001, 100, 0, 'NONE', 'BLACK', 'SHORT', 'BEGINNER', 1, 'fixture', 'BASIC', 'NONE', 9001);
                    insert into worlds (id, world_day, food, gold, season, seed, stone, weather,
                                        wood, world_name, character_id, village_template_version)
                    values (9001, 1, 0, 0, 'SPRING', 42, 0, 'SUNNY', 0, 'fixture', 9001, 2);
                    insert into world_changes (id, asset_type, created_at, display_message, focusx, focusy,
                                               message_key, world_category, character_id)
                    values (9001, 'COMMUNITY_HOUSE', current_timestamp, 'fixture', 1, 2, 'TEMPLATE_HOUSE', 'BUILDING', 9001),
                           (9002, 'COMMUNITY_HOUSE', current_timestamp, 'control', 3, 4, 'CONTROL_HOUSE', 'BUILDING', 9001);
                    insert into world_placed_objects (id, asset_type, habitat, positionx, positiony, terrain, world_change_id)
                    values (9001, 'COMMUNITY_HOUSE', 'LAND', 1, 2, 'GRASS', 9001),
                           (9002, 'COMMUNITY_HOUSE', 'LAND', 3, 4, 'GRASS', 9002);
                    insert into world_terrain_tiles (terrain_type, walkable, tile_x, tile_y, character_id)
                    values ('WATER', false, 17, 13, 9001), ('WATER', false, 22, 13, 9001),
                           ('WATER', false, 23, 11, 9001), ('WATER', false, 23, 13, 9001),
                           ('WATER', false, 23, 15, 9001), ('GRASS', false, 14, 6, 9001),
                           ('WATER', false, 16, 13, 9001);
                    """);
        }
    }

    private void assertPersistedWorldAlignment() throws SQLException {
        assertThat(queryForString("""
                select village_template_version || '|' || min_tile_x || '|' || max_tile_x || '|' ||
                       min_tile_y || '|' || max_tile_y || '|' || world_generation_version
                from worlds where id = 9001
                """)).isEqualTo("3|-8|31|-8|23|3");
        assertThat(queryForInt("select count(*) from worlds where id = 9001 and last_animal_movement_at is null"))
                .isEqualTo(1);
        assertThat(queryForStrings(postgres, """
                select chunk_x || ',' || chunk_y || '|' || template_key || '|' || generation_version
                from world_chunks where world_id = 9001 order by chunk_x, chunk_y
                """)).containsExactly("0,0|HUB_0_0|1", "0,1|HUB_0_1|1", "1,0|HUB_1_0|1",
                        "1,1|HUB_1_1|1", "2,0|HUB_2_0|1", "2,1|HUB_2_1|1");
        assertThat(queryForInt("""
                select count(*) from world_chunks where world_id = 9001 and region_type = 'HUB'
                and status = 'GENERATED' and generated_at is not null and discovered_at is not null
                """)).isEqualTo(6);
        assertThat(queryForStrings(postgres, """
                select id || '|' || focusx || '|' || focusy from world_changes order by id
                """)).containsExactly("9001|672|288", "9002|3|4");
        assertThat(queryForStrings(postgres, """
                select id || '|' || positionx || '|' || positiony || '|' || terrain
                from world_placed_objects order by id
                """)).containsExactly("9001|672|288|ROAD", "9002|3|4|GRASS");
        assertThat(queryForStrings(postgres, """
                select tile_x || ',' || tile_y || '|' || terrain_type || '|' || walkable
                from world_terrain_tiles where character_id = 9001 order by tile_x, tile_y
                """)).containsExactly("14,6|ROAD|true", "16,13|WATER|false", "17,13|BRIDGE|true",
                        "22,13|BRIDGE|true", "23,11|GRASS|true", "23,13|ROAD|true", "23,15|GRASS|true");
    }

    private static List<String> applicationSchema(PostgreSQLContainer<?> database) throws SQLException {
        // Structural catalog only: omit row data, sequence counters and Flyway's own table.
        return queryForStrings(database, """
                with tables as (
                    select c.oid, c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace
                    where n.nspname = 'public' and c.relkind in ('r', 'p') and c.relname <> 'flyway_schema_history'
                )
                select 'TABLE|' || relname as definition from tables
                union all
                select 'COLUMN|' || t.relname || '|' || a.attname || '|' || a.attnum || '|' ||
                       format_type(a.atttypid, a.atttypmod) || '|' || a.attnotnull || '|' ||
                       coalesce(pg_get_expr(d.adbin, d.adrelid), '') || '|' || a.attidentity::text || '|' || a.attgenerated::text
                from tables t join pg_attribute a on a.attrelid = t.oid
                left join pg_attrdef d on d.adrelid = t.oid and d.adnum = a.attnum
                where a.attnum > 0 and not a.attisdropped
                union all
                select 'CONSTRAINT|' || t.relname || '|' || c.conname || '|' || c.contype::text || '|' ||
                       c.convalidated || '|' || pg_get_constraintdef(c.oid)
                from tables t join pg_constraint c on c.conrelid = t.oid
                union all
                select 'INDEX|' || t.relname || '|' || i.indisvalid || '|' || pg_get_indexdef(i.indexrelid)
                from tables t join pg_index i on i.indrelid = t.oid
                order by definition
                """);
    }

    private static List<String> queryForStrings(PostgreSQLContainer<?> database, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Connection connection = database.createConnection("");
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
    }

    private void assertSpringValidatesSchema() {
        try (ConfigurableApplicationContext context =
                new SpringApplicationBuilder(ProjectEdenBackendApplication.class)
                        .web(WebApplicationType.SERVLET)
                        .run(
                                "--server.port=0",
                                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                                "--spring.datasource.username=" + postgres.getUsername(),
                                "--spring.datasource.password=" + postgres.getPassword(),
                                "--spring.datasource.driver-class-name=org.postgresql.Driver",
                                "--spring.jpa.hibernate.ddl-auto=validate",
                                "--spring.flyway.enabled=true",
                                "--spring.flyway.baseline-on-migrate=false",
                                "--spring.devtools.restart.enabled=false",
                                "--eden.dataset.enabled=false",
                                "--eden.dataset.collection.enabled=false",
                                "--eden.benchmark.orchestration.enabled=false",
                                "--eden.vision.enabled=false",
                                "--eden.image-observation.provider=mock")) {
            assertThat(context.isActive()).isTrue();
        }
    }

    private void assertRequiredTablesAndIndexes() throws SQLException {
        List<String> requiredTables = List.of(
                "users",
                "characters",
                "photos",
                "recognitions",
                "memory_taxonomy_categories",
                "memory_tags",
                "memory_classifications",
                "worlds",
                "world_changes",
                "world_placed_objects",
                "world_terrain_tiles");
        for (String table : requiredTables) {
            assertThat(queryForInt("""
                    select count(*)
                    from information_schema.tables
                    where table_schema = 'public' and table_name = '%s'
                    """.formatted(table))).as(table).isEqualTo(1);
        }

        assertThat(queryForInt("""
                select count(*) from pg_indexes
                where schemaname = 'public'
                  and indexname = 'ux_memory_classifications_legacy_recognition'
                """)).isEqualTo(1);
        assertThat(queryForInt("""
                select count(*) from pg_indexes
                where schemaname = 'public'
                  and indexname = 'idx_recognitions_planting_target_object_id'
                """)).isEqualTo(1);
    }

    private void assertFoundationSchema() throws SQLException {
        assertThat(applicationTableCount()).isEqualTo(45);
        for (String column : List.of("last_animal_movement_at", "min_tile_x", "max_tile_x",
                "min_tile_y", "max_tile_y", "world_generation_version")) {
            assertThat(queryForInt("""
                    select count(*) from information_schema.columns
                    where table_schema = 'public' and table_name = 'worlds' and column_name = '%s'
                    """.formatted(column))).as(column).isEqualTo(1);
        }
        assertThat(queryForString("""
                select data_type || ':' || is_nullable from information_schema.columns
                where table_schema = 'public' and table_name = 'worlds'
                  and column_name = 'last_animal_movement_at'
                """)).isEqualTo("timestamp without time zone:YES");
        assertThat(queryForInt("""
                select count(*) from information_schema.columns where table_schema = 'public'
                and ((table_name = 'recognitions' and column_name = 'planting_target_object_id')
                  or (table_name = 'world_changes' and column_name = 'target_object_id'))
                and data_type = 'bigint' and is_nullable = 'YES'
                """)).isEqualTo(2);
        for (String index : List.of("idx_world_terrain_tiles_character_y_x", "idx_world_changes_character_id_id",
                "idx_world_placed_objects_position", "idx_world_chunks_world_coordinate", "idx_world_chunks_world_discovered")) {
            assertThat(queryForInt("select count(*) from pg_indexes where schemaname = 'public' and indexname = '"
                    + index + "'")).as(index).isEqualTo(1);
        }
        assertThat(constraint("world_chunks", "uk_world_chunks_world_coordinate"))
                .isEqualTo("UNIQUE (world_id, chunk_x, chunk_y)");
        assertThat(constraint("world_chunks", "world_chunks_world_id_fkey"))
                .isEqualTo("FOREIGN KEY (world_id) REFERENCES worlds(id) ON DELETE CASCADE");
        assertThat(constraint("worlds", "worlds_tile_bounds_check"))
                .contains("min_tile_x <= max_tile_x", "min_tile_y <= max_tile_y");
        assertThat(constraint("recognitions", "fk_recognitions_planting_target_object"))
                .isEqualTo("FOREIGN KEY (planting_target_object_id) REFERENCES world_placed_objects(id) ON DELETE RESTRICT");
        assertThat(constraint("world_changes", "fk_world_changes_target_object"))
                .isEqualTo("FOREIGN KEY (target_object_id) REFERENCES world_placed_objects(id) ON DELETE RESTRICT");
        assertThat(constraint("world_changes", "uk_world_changes_target_object"))
                .isEqualTo("UNIQUE (target_object_id)");
        assertThat(constraint("world_changes", "world_changes_target_crop_check"))
                .contains("target_object_id IS NULL");
        assertCheckValues("world_changes", "world_changes_target_crop_check",
                List.of("FARM_CARROT", "FARM_FLOWER", "FARM_VEGETABLE", "FARM_TOMATO", "FARM_CABBAGE"));
        List<String> assets = java.util.Arrays.stream(com.projecteden.world.ecology.WorldAssetType.values())
                .map(Enum::name).toList();
        List<String> terrains = java.util.Arrays.stream(com.projecteden.world.ecology.TerrainType.values())
                .map(Enum::name).toList();
        assertCheckValues("world_changes", "world_changes_asset_type_check", assets);
        assertCheckValues("world_placed_objects", "world_placed_objects_asset_type_check", assets);
        assertCheckValues("world_placed_objects", "world_placed_objects_terrain_check", terrains);
        assertCheckValues("world_terrain_tiles", "world_terrain_tiles_terrain_type_check", terrains);
        assertCheckValues("world_chunks", "world_chunks_region_type_check", List.of("HUB", "MEADOW", "FOREST", "POND"));
        assertCheckValues("world_chunks", "world_chunks_status_check", List.of("GENERATING", "GENERATED", "FAILED"));
        assertThat(queryForInt("""
                select count(*) from information_schema.tables where table_schema = 'public'
                and table_name in ('npc_runtime_states', 'npc_conversation_states', 'npc_dialogue_sessions')
                """)).isZero();
    }

    private String constraint(String table, String name) throws SQLException {
        return queryForString("select pg_get_constraintdef(oid) from pg_constraint where conrelid = 'public."
                + table + "'::regclass and conname = '" + name + "'");
    }

    private void assertCheckValues(String table, String name, List<String> values) throws SQLException {
        List<String> actual = java.util.regex.Pattern.compile("'([^']+)'")
                .matcher(constraint(table, name)).results().map(match -> match.group(1)).toList();
        assertThat(actual).as(name).containsExactlyInAnyOrderElementsOf(values);
    }

    private int applicationTableCount() throws SQLException {
        return queryForInt("""
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_type = 'BASE TABLE'
                  and table_name <> 'flyway_schema_history'
                """);
    }

    private int queryForInt(String sql) throws SQLException {
        try (Connection connection = postgres.createConnection("");
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private String queryForString(String sql) throws SQLException {
        try (Connection connection = postgres.createConnection("");
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
