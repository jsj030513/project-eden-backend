package com.projecteden.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.projecteden.ProjectEdenBackendApplication;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class FlywayMigrationIntegrationTests {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("project_eden_migration")
                    .withUsername("eden_migration")
                    .withPassword("migration-test-only");

    @Test
    void emptyPostgresqlMigratesToLatestAndSpringValidatesSchema() throws SQLException {
        Flyway versionTen = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .target("10")
                .load();

        assertThat(applicationTableCount()).isZero();

        MigrateResult firstMigration = versionTen.migrate();

        assertThat(firstMigration.success).isTrue();
        assertThat(firstMigration.migrationsExecuted).isEqualTo(2);
        assertThat(firstMigration.targetSchemaVersion).isEqualTo("10");
        assertThat(queryForString(
                        "select type from flyway_schema_history where version = '9'"))
                .isEqualTo("SQL_BASELINE");
        assertThat(queryForString(
                        "select script from flyway_schema_history where version = '9'"))
                .isEqualTo("B9__project_eden_schema_baseline.sql");
        assertThat(queryForString(
                        "select script from flyway_schema_history where version = '10'"))
                .isEqualTo("V10__add_world_animal_movement_checkpoint.sql");
        assertThat(applicationTableCount()).isEqualTo(44);
        assertRequiredTablesAndIndexes();

        insertExistingWorld();

        Flyway boundsFlyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .target("11")
                .load();
        MigrateResult boundsMigration = boundsFlyway.migrate();

        assertThat(boundsMigration.success).isTrue();
        assertThat(boundsMigration.migrationsExecuted).isEqualTo(1);
        assertThat(boundsMigration.targetSchemaVersion).isEqualTo("11");
        assertThat(queryForString(
                        "select script from flyway_schema_history where version = '11'"))
                .isEqualTo("V11__add_finite_world_bounds.sql");
        assertThat(queryForString("""
                select concat(min_tile_x, ':', max_tile_x, ':', min_tile_y, ':', max_tile_y, ':',
                              world_generation_version)
                from worlds where world_name = 'migration-world'
                """)).isEqualTo("0:23:0:15:1");
        assertRequiredBoundsColumnsAndIndexes();

        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .load();
        MigrateResult chunkMigration = flyway.migrate();

        assertThat(chunkMigration.success).isTrue();
        assertThat(chunkMigration.migrationsExecuted).isEqualTo(9);
        assertThat(chunkMigration.targetSchemaVersion).isEqualTo("20");
        assertThat(queryForString(
                "select script from flyway_schema_history where version = '12'"))
                .isEqualTo("V12__add_world_chunks.sql");
        assertThat(queryForString(
                "select script from flyway_schema_history where version = '15'"))
                .isEqualTo("V15__align_outer_region_connectors.sql");
        assertThat(queryForString(
                "select script from flyway_schema_history where version = '16'"))
                .isEqualTo("V16__add_canonical_npc_runtime_and_dialogue.sql");
        assertThat(queryForString(
                "select script from flyway_schema_history where version = '17'"))
                .isEqualTo("V17__add_npc_affinity_foundation.sql");
        assertThat(queryForString(
                "select script from flyway_schema_history where version = '18'"))
                .isEqualTo("V18__add_npc_quest_foundation.sql");
        assertThat(queryForString(
                "select script from flyway_schema_history where version = '19'"))
                .isEqualTo("V19__add_deferred_npc_quest_event_processing.sql");
        assertThat(queryForString(
                "select script from flyway_schema_history where version = '20'"))
                .isEqualTo("V20__add_photo_ecology_placement_audit.sql");
        assertThat(queryForString("""
                select concat(village_template_version, ':', world_generation_version)
                from worlds where world_name = 'migration-world'
                """)).isEqualTo("3:3");
        assertThat(queryForString("""
                select concat(min_tile_x, ':', max_tile_x, ':', min_tile_y, ':', max_tile_y)
                from worlds where world_name = 'migration-world'
                """)).isEqualTo("-8:31:-8:23");
        assertThat(queryForInt("""
                select count(*) from world_chunks
                where world_id = (select id from worlds where world_name = 'migration-world')
                """)).isEqualTo(6);
        assertThat(queryForInt("""
                select count(*) from world_chunks
                where region_type = 'HUB' and status = 'GENERATED'
                """)).isEqualTo(6);
        assertRequiredChunkTableAndIndexes();
        assertRequiredNpcRuntimeTablesAndIndexes();
        assertThat(queryForInt("""
                select count(*) from information_schema.tables
                where table_schema = 'public'
                  and table_name in (
                    'npc_affinity_states', 'npc_affinity_events',
                    'npc_quest_states', 'npc_quest_events'
                  )
                """)).isEqualTo(4);
        assertThat(queryForInt("""
                select count(*) from information_schema.columns
                where table_schema = 'public' and table_name = 'npc_quest_events'
                  and column_name in (
                    'world_id', 'processing_status', 'eligible_quest_ids',
                    'processed_at', 'outcome_reason', 'processing_attempts'
                  )
                """)).isEqualTo(6);
        assertThat(queryForInt("""
                select count(*) from pg_indexes
                where schemaname = 'public' and indexname = 'idx_npc_quest_event_replay'
                """)).isEqualTo(1);

        MigrateResult secondMigration = flyway.migrate();

        assertThat(secondMigration.success).isTrue();
        assertThat(secondMigration.migrationsExecuted).isZero();
        assertThat(applicationTableCount()).isEqualTo(52);

        try (ConfigurableApplicationContext context =
                new SpringApplicationBuilder(ProjectEdenBackendApplication.class)
                        .web(WebApplicationType.SERVLET)
                        .run(
                                "--server.port=0",
                                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                                "--spring.datasource.username=" + POSTGRES.getUsername(),
                                "--spring.datasource.password=" + POSTGRES.getPassword(),
                                "--spring.datasource.driver-class-name=org.postgresql.Driver",
                                "--spring.jpa.hibernate.ddl-auto=validate",
                                "--spring.flyway.enabled=true",
                                "--spring.flyway.baseline-on-migrate=false",
                                "--spring.devtools.restart.enabled=false",
                                "--jwt.secret=project-eden-migration-test-only-secret-with-32-bytes",
                                "--eden.dataset.enabled=false",
                                "--eden.dataset.collection.enabled=false",
                                "--eden.benchmark.orchestration.enabled=false",
                                "--eden.vision.enabled=false",
                                "--eden.image-observation.provider=mock")) {
            assertThat(context.isActive()).isTrue();
        }
    }

    private void assertRequiredNpcRuntimeTablesAndIndexes() throws SQLException {
        for (String table : List.of(
                "npc_runtime_states",
                "npc_conversation_states",
                "npc_dialogue_sessions")) {
            assertThat(queryForInt("""
                    select count(*) from information_schema.tables
                    where table_schema = 'public' and table_name = '%s'
                    """.formatted(table))).as(table).isEqualTo(1);
        }
        for (String index : List.of(
                "idx_npc_runtime_world_coordinate",
                "idx_npc_conversation_world",
                "idx_npc_dialogue_session_active")) {
            assertThat(queryForInt("""
                    select count(*) from pg_indexes
                    where schemaname = 'public' and indexname = '%s'
                    """.formatted(index))).as(index).isEqualTo(1);
        }
    }

    private void insertExistingWorld() throws SQLException {
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert into users
                      (email, nickname, password, provider, role, status, created_at, updated_at)
                    values
                      ('migration@example.com', 'migration', 'encoded', 'LOCAL', 'USER', 'ACTIVE',
                       current_timestamp, current_timestamp)
                    """);
            statement.executeUpdate("""
                    insert into characters
                      (energy, exp, gender, hair_color, hair_style, job, level, name, outfit,
                       weapon_type, user_id, created_at, updated_at)
                    select 100, 0, 'NONE', 'BLACK', 'SHORT', 'BEGINNER', 1, 'Migration',
                           'BASIC', 'NONE', id, current_timestamp, current_timestamp
                    from users where email = 'migration@example.com'
                    """);
            statement.executeUpdate("""
                    insert into worlds
                      (world_day, food, gold, season, seed, stone, weather, wood, world_name,
                       character_id, village_template_version, created_at, updated_at)
                    select 1, 0, 0, 'SPRING', 1, 0, 'SUNNY', 0, 'migration-world',
                           id, 2, current_timestamp, current_timestamp
                    from characters where name = 'Migration'
                    """);
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
                select count(*) from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'worlds'
                  and column_name = 'last_animal_movement_at'
                """)).isEqualTo(1);
        assertThat(queryForInt("""
                select count(*) from pg_indexes
                where schemaname = 'public'
                  and indexname = 'idx_recognitions_planting_target_object_id'
                """)).isEqualTo(1);
    }

    private void assertRequiredBoundsColumnsAndIndexes() throws SQLException {
        for (String column : List.of(
                "min_tile_x",
                "max_tile_x",
                "min_tile_y",
                "max_tile_y",
                "world_generation_version")) {
            assertThat(queryForInt("""
                    select count(*) from information_schema.columns
                    where table_schema = 'public'
                      and table_name = 'worlds'
                      and column_name = '%s'
                    """.formatted(column))).as(column).isEqualTo(1);
        }
        for (String index : List.of(
                "idx_world_terrain_tiles_character_y_x",
                "idx_world_changes_character_id_id",
                "idx_world_placed_objects_position")) {
            assertThat(queryForInt("""
                    select count(*) from pg_indexes
                    where schemaname = 'public' and indexname = '%s'
                    """.formatted(index))).as(index).isEqualTo(1);
        }
        assertThat(queryForInt("""
                select count(*) from pg_constraint
                where conname = 'worlds_tile_bounds_check'
                """)).isEqualTo(1);
    }

    private void assertRequiredChunkTableAndIndexes() throws SQLException {
        assertThat(queryForInt("""
                select count(*) from information_schema.tables
                where table_schema = 'public' and table_name = 'world_chunks'
                """)).isEqualTo(1);
        for (String index : List.of(
                "idx_world_chunks_world_coordinate",
                "idx_world_chunks_world_discovered")) {
            assertThat(queryForInt("""
                    select count(*) from pg_indexes
                    where schemaname = 'public' and indexname = '%s'
                    """.formatted(index))).as(index).isEqualTo(1);
        }
        assertThat(queryForInt("""
                select count(*) from pg_constraint
                where conname = 'uk_world_chunks_world_coordinate'
                """)).isEqualTo(1);
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
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private String queryForString(String sql) throws SQLException {
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
