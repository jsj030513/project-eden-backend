# Backend B1 Flyway Migration Baseline Recovery

## Scope and decision

B1 restores the missing tracked migration lineage without rewriting any SQL that
has already been applied. The existing PostgreSQL database records versions
`V1` through `V9`; their checksums are therefore immutable. A new Flyway
baseline migration, `B9__project_eden_schema_baseline.sql`, supplies the complete
schema for a brand-new PostgreSQL database. Existing databases continue to
validate and use the original `V1`–`V9` versioned lineage.

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
| V8 | `V8__align_village_template_postgresql_constraints.sql` | Aligns asset/terrain checks | World ecology tables | Applied and tracked |
| V9 | `V9__add_targeted_world_planting_projection.sql` | Adds planting target FKs, index, uniqueness, crop check | Recognition/world object/change tables | Applied and tracked |
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
- existing schema with V1–V9 history: ignore B9 as a baseline alternative,
  validate the original checksums, and apply only future migrations.

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

The existing database is never mutated during B1 validation. Upgrade
verification uses a dump-restored temporary clone, compares representative
counts and ID ranges before/after startup, checks zero representative FK
orphans, and confirms the original V1–V9 history remains unchanged.

The B1 validation run used PostgreSQL 16.14. The empty-database test applied one
`SQL_BASELINE` migration, created 44 application tables, started Tomcat on a
random port, and passed Hibernate validation; the second migration execution
applied zero changes. The upgrade clone retained 250 users, 144 photos, 126
recognitions, 125 memory classifications, 8,635 world changes, 8,779 placed
objects, and 94,464 terrain tiles. Representative minimum/maximum IDs and all
ten original Flyway history rows were unchanged, with zero checked FK orphans.

Village template idempotency remains covered by
`VillageTemplateIntegrationTests`. The persisted PostgreSQL fixture has
template version 2, 384 terrain tiles (including 32 SOIL tiles), eight carrots,
eight flowers, four tomatoes, four cabbages, one empty farm object, four
template NPCs, one dog, one cat, and two birds. Re-reading/bootstrap does not
create duplicate template objects.

## Safety boundary

- no Flyway clean;
- no existing database/schema drop;
- no existing migration rewrite;
- no application-data seed in the baseline;
- no local database credential or filesystem path in committed files;
- no Auth, Dataset, Photo/Recognition API, or frontend production change.

## Final test evidence

- `FlywayMigrationIntegrationTests`: 1 test, 0 failures, 0 errors, 0 skips;
- `VillageTemplateIntegrationTests`: 4 tests, 0 failures, 0 errors, 0 skips;
- full worktree regression: 533 tests, 0 failures, 0 errors, 4 existing
  opt-in skips;
- both Maven package modes required by B1 completed with `BUILD SUCCESS`;
- staged and working-tree `git diff --check` completed successfully.
