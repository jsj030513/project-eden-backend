# Sprint 10 Real Photo Evaluation

## Evaluation Readiness

- Evaluation images: Not available (0 local images found in `evaluation-images/`)
- Manifest: Not available (no real evaluation manifest found)
- Case count: Not available
- OPENAI_API_KEY: Missing
- OPENAI_VISION_MODEL: Missing
- Actual evaluation possible: false

## Execution

- Actual OpenAI evaluation: Not executed
- Reason: API key, model configuration, real-image dataset, and real manifest are unavailable.
- Dry run: Completed with the existing fake-provider evaluation runner test. The run validates manifest parsing, case validation, provider/classification invocation, metric calculation, and CSV/Markdown report generation without a network call.
- Provider: Fake provider (test only)
- Date: 2026-07-11

## Dataset

- Total cases: Not executed for a real dataset
- Scored cases: Not executed for a real dataset
- Exploratory cases: Not executed for a real dataset
- Category distribution: Not available
- MIME distribution: Not available

`docs/evaluation-manifest-template.json` provides 44 anonymous, Git-safe template cases for preparing a future local dataset. It contains no images or personal paths.

## Quality Results

- Provider success rate: Not executed
- Mock fallback rate: Not executed
- UNKNOWN rate: Not executed
- Primary accuracy: Not executed
- Secondary precision: Not executed
- Secondary recall: Not executed
- Tag precision: Not executed
- Tag recall: Not executed
- Over-inference rate: Not executed

Dry-run output is intentionally not presented as image-recognition quality.

## Latency

- Average: Not executed
- P50: Not executed
- P95: Not executed
- Maximum: Not executed

## Failure Analysis

No real-provider failure analysis is available because no real evaluation was run. The dry run confirms the report path can record case-level failures without writing image paths, image bytes, base64 content, API keys, or raw provider responses.

## Security Verification

- Image path exposed: No; the dry-run assertion verifies the CSV contains the case ID but not the source image path.
- Base64 exposed: No; image bytes are not part of `ImageEvaluationResult` or report columns.
- API Key exposed: No; no key was configured or emitted.
- Raw response stored: No; evaluation produces reports only and does not persist provider responses.

## Known Limitations

- No consent-reviewed real image dataset is present locally.
- OpenAI credentials and model configuration are absent.
- The template describes expected labels only; it is not a real evaluation manifest until local image files are deliberately supplied.
- HEIC/HEIF remains a safe Mock/UNKNOWN fallback path; no conversion is included.

## Sprint 10 Exit Decision

- Backend foundation: Complete
- Real photo quality validation: Pending real dataset
- Sprint 10 complete: Conditionally complete
- Remaining blockers: A consent-reviewed manifest with at least 30 real local images, `OPENAI_API_KEY`, and `OPENAI_VISION_MODEL` are required before quality metrics can be claimed.
- Recommended next Sprint: Run the opt-in 30–50 image evaluation, review fallback/UNKNOWN rates and precision metrics, then make a separate, evidence-based decision on provider prompt or taxonomy changes.

## 2026-07-12 Real Dataset Execution Readiness Recheck

- Evaluation images: 0
- `evaluation-images/manifest.json`: Missing
- Manifest cases: Not available
- `OPENAI_API_KEY`: Missing
- `OPENAI_VISION_MODEL`: Missing
- `EDEN_IMAGE_OBSERVATION_PROVIDER`: Unset (the application default remains `mock`)
- Actual OpenAI evaluation: Not executed

The required real-dataset execution conditions are not met. No OpenAI request, partial evaluation, output cleanup, API change, database change, or frontend change was performed. This is a readiness recheck only; quality metrics remain pending rather than estimated.
