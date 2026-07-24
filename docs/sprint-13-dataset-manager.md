# Sprint 13 Dataset Manager

## Goal

Create a filesystem-only foundation for consented local evaluation data. No image is stored in Git, PostgreSQL, or a public API.

## Storage Architecture

The intended default root is `$HOME/.project-eden/datasets`, overridable with `EDEN_DATASET_ROOT`. Dataset and case identifiers are restricted to lowercase safe IDs. All managed paths are resolved beneath the configured root; absolute child paths and traversal escapes are rejected.

## Consent and Ground Truth

Storage and evaluation consent are independent from training and redistribution consent. Import requires storage plus evaluation consent. Ground truth is immutable, stable-sorted, and rejects contradictory fallback-plus-signal metadata.

## Privacy and Git Policy

Source paths, raw image bytes, EXIF/GPS, and private images must not be serialized to reports or committed. Source files are never moved; a later import implementation will create only normalized managed copies.

## STEP 2 Review Queue Boundary

STEP 1 supplies identifier, consent, ground-truth, and safe-path primitives only. Filesystem import, atomic YAML metadata, duplicate checksum index, manifest export, archive flow, and review workflow remain the next implementation boundary.

## Production Validation

Dataset metadata, case metadata, and exported manifests use a dedicated Jackson `YAMLFactory` mapper with null omission and deterministic map-key ordering. Dataset manager registration is conditional on `eden.dataset.enabled=true`; the default is false, so normal application startup does not create a dataset manager or touch the local dataset filesystem.

## Sprint 13 STEP 1 Exit Evidence

- **Filesystem integration:** A `@TempDir` integration test covers dataset creation, normalized import, duplicate rejection without a new case directory, manifest export/read, archive, and archived-case exclusion from regenerated manifests.
- **Determinism:** Dataset metadata, case metadata, and the evaluation manifest are serialized with YAML and deterministic map/case ordering. The same state is exported twenty times with byte-for-byte equality asserted.
- **Symlink security:** Absolute paths, traversal escapes, managed-path symbolic links, and source-image symbolic links are rejected. Symbolic-link tests abort with an explicit JUnit assumption only on platforms that do not support links.
- **Conditional Bean:** `FilesystemVisionDatasetManager` is absent for an unset or false `eden.dataset.enabled`, and is created only for `true`. The configured `eden.dataset.root` is used for the enabled bean.
- **Disabled startup:** With `EDEN_DATASET_ENABLED=false`, mock observation, and local PostgreSQL, Spring Boot started successfully on port 18091. Flyway validated four migrations, Hibernate initialized, and `GET /health` returned `200`; the process was then gracefully stopped.
- **Regression:** `./mvnw test` completed with 440 tests, 0 failures, 0 errors, and 3 opt-in skipped tests. `git diff --check` completed successfully.

## Production Readiness

Sprint 13 STEP 1: **PRODUCTION_READY_FOR_LOCAL_DATASET_MANAGER**. This status applies to the local filesystem dataset-management boundary only: no public API, database schema, production recognition flow, or model binary distribution is introduced.

## Remaining Risks

- The manager is intentionally local-filesystem only; access control, retention operations, and multi-process locking are outside this step.
- Symlink checks are covered on supported platforms and explicitly skipped where the filesystem does not support symbolic links.
