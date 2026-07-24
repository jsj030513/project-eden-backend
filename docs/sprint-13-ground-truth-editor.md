# Sprint 13 STEP 3 — Ground Truth Editor Foundation

## Architecture

The editor changes only `ReviewItem.groundTruth`. Prediction, review ID, dataset ID, case ID, creation time, and earlier audit entries are immutable. The editor remains filesystem-only and does not call Recognition, providers, or database repositories.

## Patch

`GroundTruthPatch` supports category, secondary categories, tags, objects, activities, relationships, fallback, and notes. A `null` field is unchanged; a provided collection replaces only its matching collection. This supports field-level editing without replacing unrelated Ground Truth fields.

## Validation

- Duplicate secondary categories, tags, objects, activities, and relationships are rejected.
- Primary and secondary categories must exist in the current seeded taxonomy code set.
- Tags must exist in the current seeded taxonomy tag set.
- Objects, activities, and relationships are retained as observation signals; they are not forced into taxonomy tags.
- `fallback=true` cannot coexist with a category, secondary category, tag, object, activity, or relationship.

## History and Audit

`review.yml` now contains an append-only `history` list of `GroundTruthEditResult` entries. Each entry records the decision, previous Ground Truth, new Ground Truth, edit time, optional editor, and notes. Approve creates the first audit entry; every correction appends another entry.

## State Policy

```text
PENDING → APPROVED → CORRECTED → CORRECTED
PENDING → REJECTED
```

Ground Truth edits are allowed only for `APPROVED` and `CORRECTED`. `REJECTED` has no Ground Truth and cannot be edited. Rejecting an approved or corrected review remains an invalid transition.

## Filesystem

Review data stays in `datasets/<dataset-id>/reviews/review-*.yml`. The case directory, original prediction, and prior history entries are not rewritten or removed. Corrected Ground Truth continues to be the value used by manifest export.

## Future UI

A future local review UI can submit a `GroundTruthPatch`, show the preserved prediction beside the editable Ground Truth, and render audit history. This step deliberately adds no controller or public API.
