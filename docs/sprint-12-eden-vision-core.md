# Sprint 12 — Eden Vision Core

## Goal

[Confirmed] This STEP proves only the local ONNX Runtime boundary on the current Java 21/macOS Apple Silicon environment. It does not add a production observation provider, modify Recognition, add an API, or persist model results.

## Platform Audit

| Item | Result |
|---|---|
| OS | macOS (local developer host) |
| Architecture | `aarch64` |
| Java | Temurin 21.0.11 |
| Runtime | `com.microsoft.onnxruntime:onnxruntime:1.27.0` |
| Java dependency license | MIT |
| JAR size | approximately 43 MiB downloaded from Maven Central |
| Native package contents | `osx-aarch64`, `linux-aarch64`, `linux-x64`, `win-x64` |

[Confirmed] `OrtEnvironment.getEnvironment()` ran successfully on the local `aarch64` JVM and returned ONNX Runtime `1.27.0`. No Rosetta process was used.

[Confirmed] The official Java installation page states Java 8+ and Maven Central availability, but its platform table currently lists macOS x64 rather than ARM64. The actual 1.27.0 JAR inspection found `osx-aarch64` libraries, and the local proof is therefore stronger evidence for this exact version than the stale table. This does not prove compatibility for every future version. [ONNX Runtime Java](https://onnxruntime.ai/docs/get-started/with-java.html)

## Native Loading

[Confirmed] `OnnxVisionRuntime.health()` is an internal-only runtime seam. It captures OS/JVM facts and returns an unavailable state with a typed error when native loading fails; it does not cause application startup failure when vision is disabled.

[Confirmed] The normal application default remains `eden.vision.enabled=false`. No ONNX session is constructed at startup, no model is loaded, and the existing mock provider stays selected.

## Model Provenance

| Field | Status |
|---|---|
| Candidate model | YOLOX-Nano (provisional) |
| Source repository | [Megvii-BaseDetection/YOLOX](https://github.com/Megvii-BaseDetection/YOLOX) |
| Repository license | Apache-2.0 |
| Published benchmark | input size 416, 0.91M parameters, 1.08G FLOPs |
| ONNX deployment | Repository documents ONNX export/ONNX Runtime deployment |
| Weight source | [Unknown] no official ONNX model artifact was approved for this repository in this STEP |
| ONNX filename/SHA-256/opset/graph | [Unknown] because no model was downloaded or committed |

[Confirmed] The upstream repository is Apache-2.0 and lists YOLOX-Nano as a light model. However, the repository's code license alone is not enough to infer redistribution terms or checksums for every linked pretrained weight. This STEP intentionally does not download an unverified ONNX file. [YOLOX repository](https://github.com/Megvii-BaseDetection/YOLOX)

## Model Integrity

[Confirmed] `VisionModelIntegrity` computes a streaming SHA-256 and returns a typed error for a missing model file. `models/*.onnx` is ignored; only `models/.gitkeep` is tracked. Full model paths, model bytes, user images, tensors, and credentials are not exposed by this utility.

## ONNX Graph

[Unknown] No approved `yolox_nano.onnx` model is present, so opset, exact input/output names, element types, tensor shapes, dynamic dimensions, model metadata, and file checksum have not been asserted. Claiming a specific graph contract before inspecting the selected official binary would be incorrect.

## YOLOX Preprocessing

[Proposed] STEP 2 must derive preprocessing from the approved export and its reference implementation: decode normalized JPEG/PNG, aspect-ratio resize, letterbox padding, RGB channel order, float32 NCHW batch input, and the exact pixel scale/mean/std. Do not add ImageNet mean/std merely by convention.

[Unknown] The input dimensions, padding fill, scale ratio, and normalization values remain unfinalized until a specific ONNX graph is selected and inspected.

## YOLOX Post-processing

[Proposed] A later step must document thresholding, objectness × class score, XYWH-to-XYXY conversion, restoration after letterboxing, NMS, top-k, and the exact COCO class mapping. No mapping to `RecognizedObject` is added in this STEP.

## Latency, Memory, and Determinism

[Confirmed] Runtime environment load succeeded deterministically for the exact local dependency.

[Unknown] Session-creation latency, first/warm inference latency, memory behavior, concurrent session behavior, output finiteness, and 20-run leak checks require an approved ONNX model and were not fabricated.

## Failure Modes

- `NATIVE_LIBRARY_LOAD_FAILED`: ONNX native library cannot load for the current platform.
- `RUNTIME_UNAVAILABLE`: Java binding or runtime initialization fails for another reason.
- `MODEL_FILE_NOT_FOUND`, `MODEL_CHECKSUM_MISMATCH`, `MODEL_LOAD_FAILED`, `MODEL_METADATA_INVALID`: reserved, typed model-boundary failures.
- `INPUT_SHAPE_INVALID`, `PREPROCESSING_FAILED`, `INFERENCE_FAILED`, `OUTPUT_SHAPE_INVALID`, `NON_FINITE_OUTPUT`: reserved for the future model execution boundary.

## Production Integration Boundary

[Confirmed] No current Recognition flow is changed. The boundary remains:

```text
NormalizedImage → ImageObservationRequest → existing ImageObservationProvider
```

[Proposed] Only after model provenance, graph inspection, preprocessing, post-processing, and repeat inference proof are complete may a `LocalImageObservationProvider` be introduced behind the existing provider resolver. It must emit observation facts only; Eden classification and world expression remain downstream.

## STEP 2 Recommendation

1. Select an official YOLOX-Nano ONNX artifact with explicit weight redistribution terms; record URL, license/notice, SHA-256, model size, opset, and graph metadata.
2. Add an opt-in integration profile that requires `EDEN_VISION_ENABLED=true`, model path, and checksum. It must never run in the default Maven suite.
3. Use `OrtSession`, tensors, and results with explicit close lifecycles; prove a blank image and approved public/synthetic object fixture for 20 deterministic runs.
4. Validate Linux x64 in CI or a deployment-equivalent host before calling the model production-ready.

## STEP 2 Artifact Source

[Confirmed] The official YOLOX `demo/ONNXRuntime/README.md` directly links the YOLOX-Nano pre-generated model in the Megvii GitHub release `0.1.1rc0`:

- Initial and resolved source: `https://github.com/Megvii-BaseDetection/YOLOX/releases/download/0.1.1rc0/yolox_nano.onnx`
- Filename: `yolox_nano.onnx`
- Size: 3,659,407 bytes on local disk (approximately 3.5 MiB)
- SHA-256: `c789161ed43c8269fcd4e67c67eeeb4e80c622da2eb296a20bc6007bd18a0b7d`
- Git tracked: no; locally operator-provisioned outside the repository

[Confirmed] The source page identifies YOLOX-Nano as 416×416, 0.91M parameters and 1.08 GFLOPs, and describes the official export procedure (default opset 11). [Official ONNX Runtime demo README](https://github.com/Megvii-BaseDetection/YOLOX/tree/main/demo/ONNXRuntime)

## Artifact Policy

[Confirmed] Local development proof is allowed. Git redistribution, application binary bundling, and remote runtime auto-download remain prohibited pending a separate weight-specific redistribution review. The official repository links this release asset, but its Apache-2.0 source-code license alone is not treated as a completed review of pretrained-weight obligations.

## ONNX Graph

| Field | Actual result |
|---|---|
| Producer | `pytorch` |
| Graph | `torch-jit-export` |
| Model domain | empty |
| Model version | `9223372036854775807` |
| Input | `images`, float32, `[1, 3, 416, 416]` |
| Output | `output`, float32, `[1, 3549, 85]` |
| Decode embedded | no; output requires YOLOX grid/stride decode |

## Preprocessing

[Confirmed] The Java preprocessor uses the official demo's behavioral contract: 416×416 aspect-ratio letterbox, top-left padding value 114, no ImageNet mean/std, BGR channel values, float32 NCHW `[1,3,416,416]`. The official demo obtains BGR through OpenCV, applies its `preproc`, then invokes `demo_postprocess`; Java mirrors those observable steps rather than adding an arbitrary normalization layer.

## Post-processing

[Confirmed] The raw 3,549 candidates are decoded with the 52×52/26×26/13×13 grids and strides 8/16/32. Each row uses `cx, cy, width, height, objectness, 80 class scores`; confidence is `objectness × classProbability`. Coordinates are restored through the letterbox scale and clamped to original bounds. Class-aware NMS uses threshold 0.45 and deterministic ordering (confidence descending, class index, x1, y1).

[Confirmed] COCO-80 labels are embedded only for local output decoding. They are not mapped into Eden `RecognizedObject` in this STEP.

## Cat Proof

[Confirmed] A public COCO validation cat image was stored outside the repository solely for proof. It was not persisted or logged by application code. The final Java opt-in run produced:

- Cat detected: yes
- Cat confidence: `0.746971`
- Cat box: `[327.1, 33.8, 622.9, 381.8]` in original-image coordinates
- Candidate detections after threshold/NMS: 4
- NaN/Infinity: none in all 3,549 × 85 output values
- Blank image: inference completed with finite output; no false-positive assertion is made because empty detection behavior is model/threshold dependent.

## Deterministic Benchmark

[Confirmed] macOS Apple Silicon CPU proof, one local run:

| Metric | Result |
|---|---|
| Session creation + checksum | 1294.6 ms |
| First inference | 26.0 ms |
| Warm min / median / p95 / max | 13.6 / 15.6 / 21.8 / 47.8 ms |
| Repeat count | 20 |
| Detection variance | none; identical `YoloXDetection` lists |
| Heap/native memory | not separately observable with the current Java-only harness |

## Limitations

1. Linux x64 and deployment container inference have not yet been proved with this exact model.
2. The current proof does not persist model provenance or raw output and does not expose it through an API.
3. Model-weight redistribution/bundling remains pending legal and product review.

## STEP 3 Provider Integration Decision

[Proposed] The runtime is technically ready for a guarded `LocalImageObservationProvider` spike, but production registration must wait for Linux proof, evaluation across representative images, and an explicit mapping policy from COCO observations to Eden facts. Recognition, classification, and world expression remain unchanged in STEP 2.

## STEP 4 Local Provider Integration

[Confirmed] `EDEN_IMAGE_OBSERVATION_PROVIDER=local` now selects `LocalImageObservationProvider`. Its `observe` method accepts only the normalized request contract, invokes the cached `LocalVisionRuntimeService`, decodes the resulting `DetectionResult` through the existing `DetectionObservationBuilder`, and returns the existing immutable `ImageObservation`. Default provider selection remains `mock`.

No public Recognition API, DTO, database schema, event, legacy dual-write writer, `MemoryClassificationService`, Village, NPC, Resonance, or Collection behavior changed. The local provider does not normalize input, write data, publish events, or contact any external provider.

## Runtime Readiness

[Confirmed] Local runtime initialization is lazy: no model is loaded during application startup. The first local observation request validates `eden.vision.enabled`, the configured model path, and the configured SHA-256 before one reusable `OrtSession` is created. Readiness is cached as `DISABLED`, `MODEL_MISSING`, `CHECKSUM_MISMATCH`, `MODEL_LOAD_FAILED`, `RUNTIME_UNAVAILABLE`, or `READY`; failed initialization is not retried on every request. The session is closed by Spring lifecycle shutdown.

`provider=local` with a disabled, missing, unreadable, or checksum-mismatched model returns the existing fallback observation (`recognized=false`, `fallback=true`). It does not fall through to OpenAI and does not fail application startup. No model download exists.

## Request Contract and Normalized Bytes Boundary

[Confirmed] The internal `ImageObservationRequest` now carries normalized-image marker, normalized JPEG/PNG bytes, content type, width, height, and SHA-256 checksum. `LocalVisionRuntimeService` rejects absent/empty bytes, unsupported content types, missing or invalid dimensions/checksum, checksum mismatch, and decoded-size mismatch before ONNX inference. `RecognitionApplicationService` already normalizes an uploaded recognition image before it calls the selected provider; its existing normalization-failure policy still uses Mock observation rather than passing original bytes to Local Vision.

The bytes are not included in `toString`, logs, persistence, DTOs, events, or external requests.

## Session Lifecycle and Inference Flow

[Confirmed] One `YoloXInferenceEngine` wraps a reusable ONNX `OrtSession` per application runtime. Each request creates and closes only its tensor/result via the existing engine. The flow is:

`NormalizedImage → ImageObservationRequest → LocalVisionRuntimeService → YoloXImagePreprocessor → YoloXInferenceEngine → YoloXOutputDecoder/NMS → DetectionResult → DetectionObservationBuilder → ImageObservation`.

The existing thresholds remain configuration-driven: confidence `0.30`, class-aware NMS `0.45`, maximum 100 detections. Observation confidence is the highest accepted detection confidence, preserving the existing builder policy. Empty accepted detections are a normal fallback observation, distinct from an inference failure.

## Fallback Semantics

[Confirmed] A malformed normalized request, runtime unavailability, preprocessing/inference/output failure, or builder failure is contained inside the Local provider and becomes fallback. Internal readiness reasons are available only in runtime state (`LOCAL_DISABLED`, `LOCAL_MODEL_MISSING`, and similar); they are not persisted or exposed through public responses. No raw image, absolute model path, full checksum, EXIF, GPS, JWT, or user data is logged by this path.

## CAT End-to-End Proof

[Confirmed] An opt-in test (`LocalVisionEndToEndProofTests`) normalizes the operator-provided public cat proof image, executes the actual local YOLOX-Nano model, and verifies `CAT` appears in the returned `ImageObservation` with `recognized=true` and `fallback=false`. It repeats the observation 20 times and sends five concurrent requests through the same runtime to validate reusable-session deterministic behavior. This test is skipped in the default Maven suite and requires explicit operator-provided model/image environment variables.

The current `MemoryClassificationService` is deliberately unchanged. Therefore the proof verifies local detection and observation conversion only; any existing CAT-to-Eden taxonomy outcome remains the current classifier's responsibility.

## Blank Image Proof

[Confirmed] The opt-in proof also passes a normalized uniform PNG through the same provider boundary and verifies a safe non-null observation result. A model false positive is not asserted as a runtime error; no accepted detection naturally produces fallback.

## Concurrency and Performance

[Confirmed] The opt-in CAT proof submits five concurrent provider calls after initialization and compares their observation maps with the first result. The same test repeats 20 sequential calls. macOS Apple Silicon inference timing from STEP 2 remains the available measurement: warm median 15.6 ms and p95 21.8 ms for direct inference. Provider-level latency is not yet recorded as a production metric; no metrics system was added in this STEP.

## Linux Status

[Unknown] macOS Apple Silicon aarch64 local inference is verified. Linux x64 and Linux aarch64 local inference have not been verified and must not be described as production-proven.

## Persistence and Event Idempotency

[Confirmed] `RecognitionApplicationService` continues to call `MemoryClassificationService`, save one Recognition, and publish one `LegacyRecognitionCompletedEvent` through the existing path. This STEP adds no persistence call, event, migration, entity, or provider-specific classification writer. Existing recognition idempotency (`findByPhotoId`) and STEP 4 legacy dual-write ownership therefore remain unchanged.

## STEP 5 Recommendation

1. Evaluate representative normalized photos on macOS and Linux before enabling `local` in a shared environment.
2. Add only evidence-backed COCO-to-Eden classification rules in the existing classification boundary; do not make local detection choose Theme or VillageChange.
3. If local inference is activated operationally, add privacy-safe latency/fallback counters and validate deployment-specific ONNX native runtime behavior.

## STEP 5 Eden Object Mapping & Conservative Rules

[Confirmed] Raw `DetectionResult` objects remain the inference source of truth. `CocoToEdenObjectMapper` creates separate in-memory mapped objects with mapping version `eden-object-map-v1`; it never overwrites detections or adds persistence. Supported COCO labels map to a narrow Eden object vocabulary and a vision-only family (`HUMAN`, `ANIMAL`, `LEARNING_TOOL`, `DIGITAL_DEVICE`, `FOOD_AND_DRINK`, `FURNITURE`, `TRANSPORT`, `OTHER`). Unsupported labels remain raw/unmapped and do not fail the pipeline.

`EdenObservationFacts` supplies deterministic counts, family facts, and box proximity. Proximity is center distance divided by the diagonal of the two-box union; overlap also qualifies. The provisional threshold is `0.25` and rules are configured by `eden.vision.rules`.

Activities use `eden-activity-rules-v1` and are accepted at `0.65`: `READING` needs PERSON+BOOK nearby; `WORK_OR_STUDY` needs PERSON+LAPTOP/MONITOR nearby; `CYCLING` needs PERSON+BICYCLE nearby; `EATING_OR_CAFE` needs PERSON, food/drink, and TABLE. WALK deliberately remains empty because YOLOX COCO evidence does not reliably establish an outdoor walk. Relationship rules (`eden-relationship-rules-v1`) require proximity and include person/cat, person/dog, person/animal, person/book, person/computer, person/bicycle, and nearby multiple persons.

Rule confidence is `min(required detection confidence) × proximity score`; no rule inflates model confidence. Activities and relationships retain deterministic confidence/rule-id ordering and explainable evidence internally. Scene inference is now conservative: COCO object co-occurrence no longer asserts INDOOR/OUTDOOR because TREE/GRASS/SKY are not supported YOLOX COCO labels and BOOK+LAPTOP is weak scene evidence.

Memory classification, Recognition APIs, persistence, events, Village/NPC, and default Mock provider remain unchanged. Current classification already recognizes CAT and BOOK/LAPTOP signals; generic `WORK_OR_STUDY` and relationships are intentionally not forced into legacy taxonomy in this STEP.

## STEP 6 Recommendation

Evaluate the mapping and thresholds with representative normalized photos before enabling the local provider beyond opt-in use. Any taxonomy integration for generic activities must be versioned and added at the classification boundary, not inside model mapping or world-expression code. Linux runtime proof remains required before production activation.

## STEP 6 Vision Taxonomy Projection & Evaluation Readiness

[Confirmed] Taxonomy seed data contains `ANIMAL`, `FOOD`, `STUDY`, `WORK`, and `EXERCISE`; it contains `CAT`, `BOOK`, `STUDYING`, and `WORKING` tags. There is no safe taxonomy target for generic `WORK_OR_STUDY`, `EATING_OR_CAFE`, or relationships. `VisionTaxonomyProjector` therefore uses `eden-vision-taxonomy-projection-v1` to project CAT/DOG/BIRD/HORSE/SHEEP/COW to ANIMAL, BOOK only to a BOOK tag, and accepted READING to STUDY. It deliberately leaves generic activities and all relationships unmapped.

Object projection threshold is 0.50, activity projection threshold 0.70, and relationship readiness threshold 0.75. Projection confidence is source confidence and is never inflated. Candidates retain source type, source/target code, confidence, rule id, and evidence; no candidate is persisted independently.

[Confirmed] `MemoryClassificationService` remains the sole classification service and read-only evaluation path. It consumes safe projection candidates in addition to its legacy signals, preserving legacy Recognition projection. Existing legacy mapping still maps BOOK directly to STUDY; this inherited behavior is documented rather than broadened in this STEP. CAT remains direct object evidence for legacy Recognition and ANIMAL taxonomy.

Evaluation now normalizes each manifest image before passing it to the selected provider, so local evaluation obeys the normalized-JPEG/PNG-only runtime boundary. It remains no-network and has no Photo, Recognition, event, or Village write path. Existing manifest/report output continues to redact absolute image paths; a richer v2 expected-object/activity/relationship schema remains a follow-up because the existing report contract is primary/secondary/tag oriented.

## STEP 7 Evaluation Manifest v2 and Linux Proof

[Confirmed] The manifest reader accepts both legacy array manifests (implicit v1) and a versioned v2 root object with `version` and `cases`. V2 cases may carry enabled state and expected object/activity/relationship/fallback fields; v1 primary/secondary/tag fields remain unchanged. Paths are consumed only for local input and are not written into existing reports.

[Proposed] `docker/vision-proof/Dockerfile` provides an opt-in Linux proof image. It contains no model or fixture; operators mount those read-only at execution. Use `--platform=linux/amd64` for the requested x64 proof. Linux x64 inference has not been executed from this macOS workspace and remains unverified; emulated latency must not be used as a production performance result.

## STEP 3 Observation Builder Foundation

[Confirmed] `DetectionResult`, `DetectionObject`, `BoundingBox`, and `DetectionConfidence` form an in-memory-only local detection boundary. `DetectionObservationBuilder` converts these objects to the existing immutable `ImageObservation` without changing `MemoryClassificationService`.

[Confirmed] The supported MVP rules are deliberately narrow:

- `PERSON` becomes the sole subject.
- Detection object codes are preserved as observation objects, including unknown codes.
- `BOOK + LAPTOP` and `CHAIR + (TABLE | DINING_TABLE)` resolve `INDOOR`; `TREE + GRASS` or `SKY` resolves `OUTDOOR`.
- `PERSON + CAT` produces the minimal `PERSON_WITH_CAT` relationship.
- Activities and mood signals remain empty.
- Empty detection returns existing-style fallback (`recognized=false`, `fallback=true`).

[Confirmed] `LocalImageObservationProvider` is resolvable only when `eden.image-observation.provider=local` is explicitly configured. Default remains `mock`. Its request-based `observe` method currently returns fallback; only `fromDetections` is implemented so STEP 4 can connect an approved inference execution path without changing public Recognition APIs.

## References

- [ONNX Runtime Java installation and platform documentation](https://onnxruntime.ai/docs/get-started/with-java.html)
- [ONNX Runtime installation matrix](https://onnxruntime.ai/docs/install/)
- [YOLOX source repository, model table, and ONNX Runtime deployment link](https://github.com/Megvii-BaseDetection/YOLOX)
