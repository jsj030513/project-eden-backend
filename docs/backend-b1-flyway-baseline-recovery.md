# Backend B1 Flyway Migration Baseline Recovery

## Scope and support contract

B1 restores the Flyway dependencies, a canonical schema baseline, and the
tracked `V1`–`V7` migration files without rewriting their SQL. Its supported
initialization path is a fresh, empty PostgreSQL database. The
`B9__project_eden_schema_baseline.sql` migration creates the canonical
Project Eden schema at version 9. Since `V1`–`V7` have lower versions than the
baseline, Flyway does not execute them individually on this fresh-baseline
path; they are not an upgrade chain applied before B9.

This unit does not provide a general-purpose in-place upgrade or validation
path for arbitrary legacy databases. In particular, it does not claim support
for a database with no Flyway history but a partial pre-existing schema, a
database with `V1`–`V7` history, a database with `V8`/`V9` history, or a
database with B9 and subsequent `V10+` history. The local Project Eden
databases are outside this canonical initialization contract. Their data was
backed up separately; any later migration or import is governed by a separate
policy.

The canonical environment starts from an empty PostgreSQL database and is
created by the repository-managed Flyway baseline. Flyway `repair`, `clean`,
checksum bypasses, and ignored-migration patterns are not compatibility
strategies. Future schema changes must be added as normal forward migrations
after canonical version 9.

This is a schema baseline, not an application-data snapshot. It contains no
rows, credentials, local paths, or `flyway_schema_history` data.

## Migration lineage audit

| Version | File | Operation | Dependency / assumption | Existing DB status |
|---|---|---|---|---|
| V1 | `V1__create_memory_taxonomy_tables.sql` | Creates taxonomy categories/tags and indexes | Empty taxonomy namespace | Retained historical migration; not run on fresh B9 path |
| V2 | `V2__create_memory_classification_tables.sql` | Creates classification/category/tag tables and FKs | `photos`, `recognitions`, V1 taxonomy tables already exist | Retained historical migration; not run on fresh B9 path |
| V3 | `V3__add_memory_classification_idempotency.sql` | Adds partial unique recognition projection index | V2 classification table | Retained historical migration; not run on fresh B9 path |
| V4 | `V4__align_recognition_object_constraint.sql` | Replaces the recognition object check | Core `recognitions` table | Retained historical migration; not run on fresh B9 path |
| V5 | `V5__allow_template_world_changes.sql` | Makes template `recognition_id` nullable | Core `world_changes` table | Retained historical migration; not run on fresh B9 path |
| V6 | `V6__add_village_template_version.sql` | Adds template version | Core `worlds` table | Retained historical migration; not run on fresh B9 path |
| V7 | `V7__add_soil_terrain_support.sql` | Documents VARCHAR terrain compatibility | No DDL | Retained historical migration; not run on fresh B9 path |
| V8 | `V8__align_village_template_postgresql_constraints.sql` | Aligns asset/terrain checks | World ecology tables | Not included in this unit or its support contract |
| V9 | `V9__add_targeted_world_planting_projection.sql` | Adds planting target FKs, index, uniqueness, crop check | Recognition/world object/change tables | Not included in this unit or its support contract |
| B9 | `B9__project_eden_schema_baseline.sql` | Creates the canonical schema at V9 | Fresh, empty PostgreSQL only | Supported initialization baseline |

No migration seeds application rows. Village template rows are created by the
idempotent application bootstrap service after a character/world exists.

## V2 predecessor resolution

The historical failure is not missing SQL inside V2: V2 was introduced into a
database where Hibernate had already created the core schema. On a truly empty
database, its foreign keys reference `photos` and `recognitions` before those
tables exist. Reordering or editing V1/V2 would invalidate the checksums already
recorded in deployed Flyway history.

The selected `B9` strategy is Flyway's fresh-environment path: on an empty
schema Flyway applies `B9` once, then applies future repository migrations
above V9. This unit makes no promise to validate or upgrade an existing schema
or migration history. Legacy database migration/import requires a separate,
explicitly designed and validated policy.

Rejected alternatives were modifying V2, fabricating a lower version below the
recorded baseline, enabling Hibernate schema creation, and disabling Flyway.
Each would either break checksum compatibility, hide migration coverage, or
make schema ownership ambiguous.

## Entity/schema audit

The baseline contains 44 application tables. A Spring context using PostgreSQL
16 and `spring.jpa.hibernate.ddl-auto=validate` successfully validates the
current JPA mappings against those tables. Dedicated assertions cover the core
user/photo/recognition tables, taxonomy/classification tables, world ecology
tables, the classification idempotency index, and the targeted-planting index.

H2 remains the fast unit/integration-test database with Flyway disabled in the
existing test profile. PostgreSQL migration correctness is covered separately
by `FlywayMigrationIntegrationTests` using an ephemeral `postgres:16`
Testcontainers database. This avoids pretending the PostgreSQL dump-style
baseline is portable SQL and does not redesign the rest of the test suite.

## Verification protocol

`FlywayMigrationIntegrationTests` proves:

1. the PostgreSQL schema begins with zero application tables;
2. Flyway selects `B9` with history type `SQL_BASELINE`;
3. migration reaches schema version 9 with 44 application tables;
4. required tables and indexes exist;
5. a second migrate executes zero migrations;
6. a servlet Spring context starts on a random port;
7. Hibernate schema validation succeeds.

The clean integration worktree uses only an ephemeral PostgreSQL Testcontainers
database. It does not connect to or mutate a deployed database. Legacy-database
upgrade validation is outside this unit's support contract and is not implied
by the presence of historical `V1`–`V7` files in the repository.

## Safety boundary

- no Flyway clean;
- no existing database/schema drop;
- no existing migration rewrite;
- no application-data seed in the baseline;
- no local database credential or filesystem path in committed files;
- no Auth, Dataset, Photo/Recognition API, or frontend production change.

## Final test evidence

- production compile and test compile passed;
- `FlywayMigrationIntegrationTests`: 1 test, 0 failures, 0 errors, 0 skips;
- full suite: 248 tests, 0 failures, 0 errors, 0 skips;
- Maven package completed with `BUILD SUCCESS`;
- `git diff --check` passed.
