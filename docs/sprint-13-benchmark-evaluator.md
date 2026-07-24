# Sprint 13 STEP 6 — Benchmark Evaluator Foundation

## Architecture

The evaluator compares caller-supplied predictions against a revision's immutable `manifest.yml`. It does not execute inference, invoke a provider, write temporary evaluation data, or read live review files.

```text
Revision manifest + caseId → prediction map → EvaluationResult / BenchmarkMetrics → BenchmarkManager.finishRun
```

## Evaluation Flow

`evaluateRevision` validates the revision and its snapshot manifest, converts each manifest expected value to `VisionGroundTruth`, and requires one prediction for every case ID. `evaluateAndFinish` additionally verifies that the target benchmark run belongs to the same revision before passing only final metrics to the existing benchmark manager.

## Metrics

- Category accuracy: exact primary-category matches divided by case count.
- Tag/object/activity/relationship precision and recall: micro-averaged set overlap.
- Tag F1: harmonic mean of tag precision and recall.
- Fallback rate: predictions marked `fallback=true` divided by case count.
- Unknown rate: non-fallback predictions with no primary category divided by case count.

No inference is performed; the metrics reflect only supplied prediction versus snapshot Ground Truth comparison.

## Validation

- Missing revision: `REVISION_NOT_FOUND`
- Missing snapshot manifest: `REVISION_MANIFEST_NOT_FOUND`
- Missing case prediction: `PREDICTION_NOT_FOUND`
- Missing Ground Truth or run/revision mismatch is rejected before a benchmark is completed.

## Future Real Inference

A later opt-in evaluator may produce the prediction map through a controlled local runtime or provider. It must preserve this evaluator's revision-bound inputs, avoid modifying snapshot data, and pass only calculated metrics to `finishRun`.
