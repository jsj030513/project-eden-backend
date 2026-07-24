# Sprint 14 STEP 1 — Representative Dataset Plan & Collection Protocol

## Goal

This filesystem-only foundation lets a developer define what local evaluation data is needed, record provenance and consent, and calculate current cohort coverage. It does not train a model, enable a provider, collect user data, or add a public API.

## Collection Plan Model

`CollectionPlan` is stored as `collection/plans/<plan-id>.yml`. A plan begins in `DRAFT` and supports only `DRAFT → ACTIVE → COMPLETED`, plus archive from DRAFT, ACTIVE, or COMPLETED. `createdAt` is immutable; `updatedAt` changes on valid mutation. Cohort IDs, plan IDs, dataset IDs, and case IDs use safe lowercase identifiers.

## Representative Dimensions

The initial canonical, uppercase dimensions are:

- `LIGHTING`: BRIGHT, NORMAL, DARK, BACKLIT
- `DISTANCE`: CLOSE, MEDIUM, FAR
- `ANGLE`: FRONT, SIDE, TOP, LOW, MIXED
- `BACKGROUND`: SIMPLE, CLUTTERED, NATURAL, INDOOR, OUTDOOR
- `IMAGE_QUALITY`: CLEAR, BLURRY, LOW_LIGHT, PARTIALLY_OCCLUDED
- `SUBJECT_COUNT`: SINGLE, MULTIPLE

`SEASON` and `INDOOR_OUTDOOR` are also reserved for local planning. Unknown or lowercase dimension values are rejected; values are never silently normalized.

## Source and Consent Policy

Case metadata records source type, collected time, an optional anonymous collector ID, consent state, license type, filename, and notes. Eligible benchmark sources are:

- SYNTHETIC or DEVELOPER_CAPTURED with NOT_REQUIRED consent
- CONSENTED_PARTICIPANT with EXPLICITLY_GRANTED consent
- PUBLIC_LICENSED with NOT_REQUIRED consent and CC0, CC_BY, or OTHER_APPROVED licensing

UNKNOWN, PENDING, REVOKED, and unsupported license/consent combinations are retained as metadata but are benchmark-ineligible and receive a warning. This preserves auditability without treating unverified data as evaluation input.

## Benchmark Eligibility

Collection metadata is stored separately at `collection/cases/<case-id>.yml`; existing `case.yml` semantics are unchanged. A case is eligible only when the source/consent policy passes. Coverage counts each eligible case once globally, while a case may match every cohort whose dimensions and tag rules it satisfies.

## Filesystem Layout

```text
datasets/<dataset-id>/collection/
  plans/<plan-id>.yml
  cases/<case-id>.yml
  reports/<plan-id>-coverage.yml
```

All YAML writes are deterministic and atomic. Plan, case, and report paths are resolved under the dataset root; traversal, absolute paths, and symlink paths are rejected.

## Coverage Calculation

For each cohort, matching requires all requested dimensions, all required tags, no excluded tags, and benchmark eligibility. Coverage is `matched / target * 100`; zero cohort targets are rejected. Cohort status is NOT_STARTED, INSUFFICIENT, TARGET_MET, or EXCEEDED. If cohort targets do not allocate the plan total, `UNALLOCATED_TARGET_CASES` is reported.

## Privacy Boundary

No real name, email, telephone number, address, device identifier, GPS, face identifier, JWT, or Authorization header is part of this schema. Collector IDs are optional local anonymous IDs matching `[a-z0-9][a-z0-9-_]{0,63}`. The collection manager performs no network call and does not inspect a user gallery.

## Local Collection Workflow

```text
Create Collection Plan
→ import synthetic or developer-owned image with Dataset Manager
→ register collection case metadata
→ review and confirm/correct Ground Truth
→ generate coverage report
→ collect only missing cohorts
→ create immutable revision
→ run offline benchmark
```

## Data That Must Not Enter Git

Private images, generated datasets, generated coverage reports, local absolute paths, model binaries, and unverified public images must not be committed. Tests use only `@TempDir` fixtures.

## Direct Developer Test Gate

Direct local collection preparation is allowed only with synthetic, developer-owned, or explicitly consented data. Collection infrastructure is disabled by default with `eden.dataset.collection.enabled=false`.

## Friend Handoff Gate

Blocked. This STEP creates local collection preparation only; it does not authorize external participant collection or app distribution.

## Production Activation Gate

Blocked. No production provider, model inference default, public endpoint, or training flow is enabled by this work.

## Example Targets

The following is a developer-only planning example, not a production threshold: total 120 cases with bright-close-clear, normal-medium-clear, dark-medium, outdoor-mixed-angle, cluttered-background, and partial-occlusion cohorts targeted at 20 cases each.

## Known Limitations

- Collection metadata is filesystem-local and has no user interface or public API.
- Coverage evaluates recorded Ground Truth/tags; it does not run inference.
- Collection policy is conservative and does not replace legal license review.

## Sprint 14 Next Step

Use a reviewed local plan to collect only the cohorts reported as missing, then create a new immutable revision and compare offline benchmark results. Model training and production activation remain separate gated decisions.
