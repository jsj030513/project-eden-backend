# Image Observation Evaluation Guide

## Purpose

This guide explains how to run the Sprint 10 STEP 7 image observation evaluation tool.

The evaluation runner is opt-in and does not write Project Eden domain data.

It does not create:

- `Photo`
- `Recognition`
- `VillageMemory`
- `MemoryClassification`
- events
- rewards

## Manifest Format

Use a JSON array. Image files should stay outside Git.

```json
[
  {
    "caseId": "cat-window-001",
    "imagePath": "/absolute/path/to/evaluation-images/cat-window.jpg",
    "expectedPrimary": "ANIMAL",
    "expectedSecondary": ["DAILY_LIFE"],
    "expectedTags": ["CAT"],
    "expectedRecognized": true,
    "notes": "Cat near window"
  }
]
```

## Local Directories

Recommended local-only paths:

- `evaluation-images/`
- `evaluation-output/`

Both are ignored by Git.

## Run

```bash
EDEN_IMAGE_EVALUATION_ENABLED=true \
EDEN_IMAGE_EVALUATION_MANIFEST=/absolute/path/to/manifest.json \
EDEN_IMAGE_EVALUATION_OUTPUT=/absolute/path/to/evaluation-output \
EDEN_IMAGE_EVALUATION_MAX_CASES=100 \
EDEN_IMAGE_OBSERVATION_PROVIDER=mock \
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

For OpenAI evaluation, also set:

```bash
EDEN_IMAGE_OBSERVATION_PROVIDER=openai
OPENAI_API_KEY=...
OPENAI_VISION_MODEL=...
```

Do not commit `.env` or real API keys.

## Outputs

The runner writes:

- `sprint-10-step-7-results.csv`
- `sprint-10-step-7-summary.md`

Reports intentionally exclude:

- raw image paths
- image bytes
- base64
- API keys
- JWTs
- raw provider responses

## Metrics

The summary includes:

- total cases
- provider success/failure
- mock fallback count
- UNKNOWN count
- primary accuracy
- secondary precision/recall
- tag precision/recall
- latency average / p50 / p95
- MIME breakdown
- failure breakdown

## Safety Notes

- Default automated tests do not call OpenAI.
- Evaluation is disabled unless `EDEN_IMAGE_EVALUATION_ENABLED=true`.
- HEIC/HEIF is not converted; unsupported MIME falls back safely.
- The evaluation tool is for measuring observation/classification quality, not for creating gameplay state.
