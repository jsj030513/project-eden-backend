# Sprint 13 STEP 5 — Benchmark Runner Foundation

## Architecture

The benchmark foundation records supplied evaluation metrics against an immutable dataset revision. It does not execute a model, invoke a provider, or change the existing evaluation runner.

```text
Dataset revision → BenchmarkRun (PENDING) → supplied BenchmarkMetrics → COMPLETED run + report
```

## Filesystem

```text
datasets/<dataset-id>/
  benchmarks/
    run-000001/
      benchmark.yml
      report.md
```

`benchmark.yml` contains the run ID, revision ID, timestamps, model, provider, revision dataset/manifest checksums, status, optional metrics, and optional safe failure metadata. `report.md` is atomically regenerated for terminal outcomes. `quality-gate.yml` is immutable and contains the corresponding quality decision.

## Metrics

`BenchmarkMetrics` records category accuracy; tag, object, activity, and relationship precision/recall; fallback and unknown rates; and case count. All rate metrics must be in the inclusive `[0, 1]` range.

## Validation and Immutability

- A run can only be created for an existing revision.
- Revision checksums are copied at run creation.
- Run IDs are sequential `run-000001` identifiers and are not overwritten.
- Completing a run requires supplied metrics and creates `report.md`.
- A run can be marked RUNNING and then failed with safe failure metadata.
- Completed and failed runs are immutable.
- A retry creates a new run ID rather than modifying a terminal run.

## Future Evaluation

A future evaluator can load a revision manifest, execute local or opt-in providers, calculate `BenchmarkMetrics`, and call `finishRun`. Provider adapters, model execution, and production activation remain deferred.

## Regression Evidence

Sprint 13 final validation exercises benchmark filesystem lifecycle and quality-gate persistence together with manifest-path resolver security tests. Generated benchmark artifacts stay under their run directory; dataset, review, and revision snapshots are not mutation targets of orchestration.

## Real Filesystem Evidence

The supplied-success, supplied-failure/retry, mock-success, and local-disabled paths use the filesystem managers and the real benchmark orchestrator. Each terminal run contains only `benchmark.yml`, `quality-gate.yml`, and `report.md`. The first terminal run is checksum-compared after retry/two-run execution to verify terminal immutability.

## Filesystem Mutation Boundary

SHA-256 snapshots exclude `benchmarks/**` when comparing immutable dataset/revision sources. Relative-path snapshots separately assert that additions are limited to the run IDs created by the current test and that no source paths are removed. E2E teardown assertions reject leftover atomic-write temporary entries.

The final Maven regression completed successfully with 469 tests, 0 failures, 0 errors, and 3 opt-in skips. `git diff --check` also completed successfully.
