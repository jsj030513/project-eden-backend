# Mobile Photo Upload and Dialogue Validation

## Investigated upload contract

- Frontend request: `POST /api/photos` through `src/api/photoApi.js`.
- Request body: `multipart/form-data` with required `file`; `plantId` is only sent when present.
- Authentication: the shared HTTP client attaches `Authorization: Bearer <JWT>` without manually setting a multipart content type.
- Recognition request: `POST /api/photos/{photoId}/recognize`.

## Actual local evidence — 2026-07-15

The most recent persisted upload had a JPEG content type, a non-zero size, no plant association, and a recognition row with `UNKNOWN`, confidence `0`, and `recognized=false`. It created one `UNKNOWN` Village Memory. No Resonance row was created. This matches the current recognition contract: recognition records Village Memory and Evolution, but does not invoke `ResonanceService` or grant a reward.

Before this change, `PhotoService` persisted only `Photo` metadata. No file existed at the generated image URL, so `/recognize` supplied no image bytes. The active process had no explicit `EDEN_IMAGE_OBSERVATION_PROVIDER` or Vision environment override; the configured default is `mock`. The effective result was therefore the filename-based `LEGACY_MOCK` fallback. No OpenAI or local ONNX inference was possible for that upload.

Provider provenance is not persisted on the legacy `Recognition` entity. The safe recognition log now records only photo ID, provider, model version, recognized/fallback state, and confidence; it never logs image bytes, JWTs, email, or absolute paths.

## Remediation

- New uploads are atomically stored under `EDEN_PHOTO_STORAGE_ROOT` (default: `$HOME/.project-eden/uploads/photos`).
- The existing `/uploads/photos/{storedFileName}` URL is backed by this storage location.
- Recognition loads the persisted bytes, normalizes them, then invokes the configured provider. Existing metadata-only historical rows remain compatible and fall back to the legacy request path when their files are absent.
- The default provider remains `mock`; no provider or production activation changed.

## Mobile capture flow

- File input values are cleared immediately after selection, so selecting the same image raises a new change event.
- A selected preview now offers **remember**, **retake**, **choose another**, and **cancel**.
- Preview object URLs are revoked before replacement, on cancellation, after completion, and on component unmount.
- Upload controls are disabled only while upload, recognition, or village refresh is pending. Input errors have a recoverable “choose another photo” state.
- A completed recognition returns to the village, starts the existing reveal, and now shows the success toast for 4.2 seconds; the capture control remains available for the next photo.

## NPC dialogue layout

The NPC bubble remains attached to the NPC wrapper’s top center. Mobile dialogue sheets are bounded by safe-area-aware offsets, have scroll containment, and enforce 44px action targets. The interaction prompt and dialogue sheet are mutually exclusive in `VillagePage`.

## Validation

- Backend targeted tests: `PhotoStorageServiceTests`, `PhotoIntegrationTests`, and `RecognitionIntegrationTests` passed (30 tests, 0 failures/errors).
- Full backend Maven regression at the time of this evidence run passed with 0 failures and 0 errors; later recovery work expanded the suite, so use the current Maven summary for the release count.
- Frontend: `npm run lint` and `npm run build` passed.
- iPhone Safari physical-device validation: **NOT EXECUTED in this environment**. Validate the full retake flow on 375×667, 390×844, 393×852, and 430×932 before accepting mobile release.
