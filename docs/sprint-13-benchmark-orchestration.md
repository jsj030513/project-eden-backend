# Benchmark Orchestration

## Goal

Provide an opt-in, offline bridge from a revision-bound supplied prediction map to the existing evaluator and benchmark manager.

## Offline Boundary

The default configuration is disabled. The orchestration never reads live review state, invokes a provider, performs inference, writes domain data, or activates production recognition.

## Benchmark Lifecycle

`PENDING → RUNNING → COMPLETED | FAILED` is persisted in `benchmark.yml`. Terminal runs are immutable; a retry is a newly created run. The legacy `finishRun` path remains compatible with a PENDING run, while orchestration explicitly marks a run RUNNING first.

## Failure Persistence

Failed runs retain a safe failure code, safe message, failure timestamp, and processed/total case counts. Metrics are absent for a failed run. Stack traces, absolute paths, image data, and provider credentials are never written to benchmark artifacts.

## Prediction Sources

`SUPPLIED` is implemented for deterministic offline execution. `MOCK`, `LOCAL`, and `OPENAI_OPTIONAL` are reserved source types; `LOCAL` returns `LOCAL_PROVIDER_DISABLED` until a separate opt-in adapter and runtime readiness boundary are approved. No network call is made.

## Quality Gate

Policy version `eden-benchmark-quality-gate-v1` evaluates supplied metrics with provisional thresholds. Fewer than 20 cases produces `INSUFFICIENT_SAMPLE`; otherwise the result is `PASS` or `FAIL`. The decision is benchmark quality evidence, never a production activation decision.

## Quality Gate Persistence

Every terminal orchestration outcome receives an immutable, atomically written `quality-gate.yml`. Failed runs receive `EVALUATION_FAILED`. A second write for the same run fails with `QUALITY_GATE_IMMUTABLE`.

## Completed Report Format

Completed reports contain a `## Quality Gate` section with the decision, policy version, case count, sample sufficiency, reasons, and an evidence heading. The report is regenerated atomically after the gate is persisted.

## Failed Report Format

Failed reports contain a `## Failure` section (code, safe message, processed/total cases, and failed time) followed by `## Quality Gate` with `EVALUATION_FAILED`.

## Conditional Configuration

`eden.benchmark.orchestration.enabled` defaults to `false`. The orchestrator and filesystem quality-gate store are conditional beans and do not require a local model, provider, or network when disabled.

## STEP 7A Exit Decision

Lifecycle and quality-gate persistence unit coverage is present. Supplied orchestration end-to-end and conditional Spring context evidence must be completed before declaring STEP 7A fully closed.

## STEP 7B Boundary

STEP 7B may introduce a separately approved MOCK or LOCAL prediction adapter and revision-image resolver. It must not alter the offline supplied-prediction boundary without independent runtime validation.

## Prediction Source Resolver Interface

`BenchmarkPredictionSourceResolver` is the orchestration dependency. Spring continues to inject the existing conditional `FilesystemBenchmarkPredictionSourceResolver`; its source-selection behavior and property conditions are unchanged.

## Test Fixture Decoupling

Plain JUnit fixtures can inject deterministic resolver implementations without a Spring context, DataSource, EntityManager, or taxonomy repositories. This seam does not alter production benchmark persistence or provider selection.

## Full Regression Evidence

The resolver seam compiled successfully and the complete Maven test suite passed after the interface migration.

## MOCK Real Filesystem E2E

The benchmark fixture creates a dataset, review-approved ground truth, immutable revision, and then deletes the live managed image. Its test-only prediction source resolves `revision/manifest.yml` image paths through `RevisionImageResolver`, normalizes the revision image, and invokes the real filesystem orchestrator. Two runs have distinct run IDs and equal metrics.

## Revision-only Benchmark

MOCK benchmark execution does not use a live dataset image fallback. It requires the immutable revision image snapshot.

## LOCAL-disabled Real Filesystem E2E

The local-disabled E2E resolves and normalizes the immutable revision image after the live managed image is deleted. It then persists a `FAILED` run with `LOCAL_PROVIDER_DISABLED`, no metrics, and a `PLATFORM_UNVERIFIED` quality gate. It does not substitute a mock success or invoke local inference.

## MOCK Regression Evidence

The targeted `BenchmarkOrchestrationObservationE2ETests` run passed without skips. The full Maven regression was then run successfully; MOCK E2E remains enabled and is not guarded by an assumption.

## SUPPLIED Failure SHA-256 Audit

The supplied failure-and-retry E2E snapshots every non-benchmark dataset file before execution. After two failed runs it verifies source checksums are identical, no source paths are removed, and additions are limited to the two run directories.

## Terminal Run Immutability

The first failed run directory is checksum-snapshotted before retry; its benchmark YAML, quality-gate YAML, and report remain byte-identical after the second terminal run is created.

## Run File Allowlist

Each supplied failed run contains exactly `benchmark.yml`, `quality-gate.yml`, and `report.md`.

## SHA-256 Side-effect Audit

The supplied, mock two-run, and local-disabled E2Es snapshot all non-benchmark regular files with deterministic relative-path SHA-256 maps before and after execution. Dataset metadata, case metadata, review files, current manifest, revision metadata, revision dataset/manifest/summary files, and revision images remain byte-identical. Added paths are limited to the specific benchmark run directories and removed paths are rejected.

## Terminal Run and Temporary-file Audit

Each run directory has the strict direct-file allowlist `benchmark.yml`, `quality-gate.yml`, and `report.md`. Retry/two-run paths checksum the first run after the second run is created. Test-only audit support rejects leftover `.tmp`, `.temp`, `.part`, `.pending`, `.bak`, `.swp`, `.tmp-*`, and `.temp-*` entries at every supplied, mock, and local-disabled E2E endpoint.

## Application and Network Boundary

The real filesystem fixtures instantiate filesystem dataset/review/revision/benchmark managers, evaluator, quality-gate store, image resolver, normalizer, and orchestrator only. They do not construct Photo, Recognition, Village, persistence repository, event-publisher, HTTP, or OpenAI-client dependencies. SUPPLIED, test-only MOCK, and LOCAL-disabled sources make no network call; actual local inference remains opt-in.

## Final Validation

Revision images are resolved only from the immutable revision manifest `imagePath`, relative to the revision root. Absolute paths, URI paths, traversal, symlinks, missing files, and unsupported image formats are rejected. The resolver never falls back to directory scanning.

The default orchestration setting remains disabled. Unit coverage verifies the manifest-path security boundary, supplied prediction-map preservation, existing mock observation behavior, conditional orchestration configuration, and quality-gate decisions. The local model proof remains opt-in and was not executed during the standard suite.

## Sprint 13 Exit Boundary

The final validation suite provides offline filesystem evidence for immutable revisions, terminal benchmark artifacts, and orchestration side-effect boundaries. It permits direct local test preparation only after the complete regression result is recorded. Friend handoff and production activation remain outside this validation scope.

Final validation executed the supplied, mock, local-disabled, and audit-helper E2Es without skips, then completed `./mvnw test` with 469 tests, 0 failures, 0 errors, and 3 opt-in skips. `git diff --check` passed.
