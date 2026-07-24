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
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .load();

        assertThat(applicationTableCount()).isZero();

        MigrateResult firstMigration = flyway.migrate();

        assertThat(firstMigration.success).isTrue();
        assertThat(firstMigration.migrationsExecuted).isEqualTo(1);
        assertThat(firstMigration.targetSchemaVersion).isEqualTo("9");
        assertThat(queryForString(
                        "select type from flyway_schema_history where version = '9'"))
                .isEqualTo("SQL_BASELINE");
        assertThat(queryForString(
                        "select script from flyway_schema_history where version = '9'"))
                .isEqualTo("B9__project_eden_schema_baseline.sql");
        assertThat(applicationTableCount()).isEqualTo(44);
        assertRequiredTablesAndIndexes();

        MigrateResult secondMigration = flyway.migrate();

        assertThat(secondMigration.success).isTrue();
        assertThat(secondMigration.migrationsExecuted).isZero();
        assertThat(applicationTableCount()).isEqualTo(44);

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
