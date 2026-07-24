# Sprint 13 STEP 4 — Dataset Versioning Foundation

## Architecture

Dataset Versioning is a separate filesystem-only snapshot boundary. It does not modify import, review, archive, or manifest-export behavior. A caller creates a revision after an approved review, correction, archive, or manifest export when an immutable checkpoint is required.

## Revision

`DatasetRevision` uses ordered IDs such as `rev-000001` and records dataset ID, creation time, optional creator/reason, case count, `ACTIVE` status, and SHA-256 checksums for the dataset, manifest, and summary files. `RevisionStatus` also reserves `ARCHIVED` and `SUPERSEDED` for a future lifecycle step; existing revisions are not mutated by this foundation.

## Snapshot Filesystem Layout

```text
datasets/<dataset-id>/
  revisions/
    rev-000001/
      revision.yml
      dataset.yml
      manifest.yml
      summary.yml
```

The snapshot copies the current dataset metadata and manifest bytes. `summary.yml` records case count, review-status counts, published Ground Truth category counts, and tag counts. The revision directory is created once through a temporary directory and atomic move where supported.

## Validation and Immutability

- Revision IDs are validated as `rev-000000` style identifiers.
- Existing revision directories are never overwritten.
- A snapshot with identical dataset, manifest, and summary checksums is rejected as `DUPLICATE_REVISION_SNAPSHOT`.
- There is no delete or update operation.

## Future Benchmark

A benchmark runner can receive a revision ID and evaluate the snapshot manifest rather than mutable live dataset files. Revision activation, retention, diff reports, and multi-process locking are intentionally outside this foundation.

## Immutable Revision Evidence

The real filesystem E2E fixtures import a managed image, create a review-approved revision, and then delete the live managed image before benchmark execution. The revision image remains the only permitted benchmark input. Tests verify byte-for-byte revision-image preservation after a live-image overwrite and deletion, as well as unchanged revision metadata, manifest, and summary files during supplied, mock, and local-disabled benchmark paths.

## Sprint 13 Exit Evidence

Revision snapshots are immutable benchmark inputs. Benchmark-only artifacts are created below `benchmarks/<run-id>/`; revision files and `revision/images/**` are checksum-audited as immutable sources. This statement records filesystem test evidence only and does not enable distribution or production activation.

Final validation ran `./mvnw test` successfully with 469 tests, 0 failures, 0 errors, and 3 opt-in skips.
