# Sprint 13 STEP 2 — Review Queue & Ground Truth Editor Foundation

## Architecture

The review queue is filesystem-only. A review captures the original prediction and, after human action, an optional independent ground truth. It does not call providers, write to PostgreSQL, or change public APIs.

```text
Prediction → review.yml → approve / correct / reject → manifest export
```

## Filesystem Layout

```text
datasets/<dataset-id>/
  cases/<case-id>/
  reviews/review-0001.yml
  manifests/evaluation-v2.yml
```

Case directories are immutable from the review queue's perspective. Review files use `eden-review-schema-v1` YAML and retain both `prediction` and `groundTruth`.

## Review Status and State Transitions

| Current | Action | Result |
| --- | --- | --- |
| `PENDING` | approve | `APPROVED`, prediction copied as ground truth |
| `PENDING` | correct | `CORRECTED`, editor-supplied ground truth stored |
| `PENDING` | reject | `REJECTED`, no ground truth |
| `APPROVED` | approve | idempotent |
| `CORRECTED` | correct | allowed to revise the correction |
| `REJECTED` | reject | idempotent |
| any other transition | approve/correct/reject | `INVALID_REVIEW_TRANSITION` |

Only one review may exist for a dataset/case pair. This prevents conflicting published ground truth.

## Dataset Integration

`APPROVED` and `CORRECTED` reviews contribute their review ground truth to `evaluation-v2.yml`. `PENDING` and `REJECTED` reviews are excluded. Existing cases with no review preserve the Sprint 13 STEP 1 `CONFIRMED`/`CORRECTED` export behavior.

## Audit Policy

The AI prediction is never overwritten. Correction writes a separate ground truth object and rejection keeps `groundTruth: null`. Review writes use a temporary file plus atomic move where supported.

## Future UI

The foundation is intentionally controller-free. A future local UI can list pending reviews, render prediction evidence, submit a decision, and display the preserved audit trail without changing the dataset format.
