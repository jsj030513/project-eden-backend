# Backend B1 Flyway Migration Baseline Recovery

## Scope and decision

B1 restores the Flyway dependencies and the DB baseline plus the tracked `V1`–`V7`
migrations without rewriting applied SQL. Deployed PostgreSQL databases record
versions `V1` through `V9`, whose checksums are immutable. This unit does not
include the `V8` and `V9` upgrade scripts; those exact scripts must be restored
by their dependent feature units before the existing-database upgrade path is
considered complete. The new `B9__project_eden_schema_baseline.sql` supplies the
complete post-V9 schema for a brand-new PostgreSQL database.

This is a schema baseline, not an application-data snapshot. It contains no
rows, credentials, local paths, or `flyway_schema_history` data.

## Migration lineage audit

| Version | File | Operation | Dependency / assumption | Existing DB status |
|---|---|---|---|---|
| V1 | `V1__create_memory_taxonomy_tables.sql` | Creates taxonomy categories/tags and indexes | Empty taxonomy namespace | Applied; preserve checksum |
| V2 | `V2__create_memory_classification_tables.sql` | Creates classification/category/tag tables and FKs | `photos`, `recognitions`, V1 taxonomy tables already exist | Applied; preserve checksum |
| V3 | `V3__add_memory_classification_idempotency.sql` | Adds partial unique recognition projection index | V2 classification table | Applied; preserve checksum |
| V4 | `V4__align_recognition_object_constraint.sql` | Replaces the recognition object check | Core `recognitions` table | Applied; preserve checksum |
| V5 | `V5__allow_template_world_changes.sql` | Makes template `recognition_id` nullable | Core `world_changes` table | Applied; preserve checksum |
| V6 | `V6__add_village_template_version.sql` | Adds template version | Core `worlds` table | Applied; preserve checksum |
| V7 | `V7__add_soil_terrain_support.sql` | Documents VARCHAR terrain compatibility | No DDL | Applied; preserve checksum |
| V8 | `V8__align_village_template_postgresql_constraints.sql` | Aligns asset/terrain checks | World ecology tables | Deployed; follow-up unit must restore the original checksum |
| V9 | `V9__add_targeted_world_planting_projection.sql` | Adds planting target FKs, index, uniqueness, crop check | Recognition/world object/change tables | Deployed; follow-up unit must restore the original checksum |
| B9 | `B9__project_eden_schema_baseline.sql` | Creates the complete schema at V9 | Brand-new empty PostgreSQL only | New baseline |

No migration seeds application rows. Village template rows are created by the
idempotent application bootstrap service after a character/world exists.

## V2 predecessor resolution

The historical failure is not missing SQL inside V2: V2 was introduced into a
database where Hibernate had already created the core schema. On a truly empty
database, its foreign keys reference `photos` and `recognitions` before those
tables exist. Reordering or editing V1/V2 would invalidate the checksums already
recorded in deployed Flyway history.

The selected `B9` strategy is Flyway's new-environment path:

- empty schema: apply `B9` once, then apply future migrations above V9;
- existing schema: the original `V1`–`V9` scripts and checksums must be present
  before validating/upgrading it; this unit restores `V1`–`V7`, while the
  dependent feature units still need to restore `V8` and `V9`.

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
database. It does not connect to or mutate a deployed database. Existing-database
upgrade validation is outside this unit's verification; it depends on restoring
the immutable V8/V9 scripts in their dependent feature units.

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
