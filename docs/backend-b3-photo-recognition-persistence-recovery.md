# Backend B3 Photo/Recognition Persistence Recovery

## Scope

B3 restores the authenticated photo upload and recognition persistence flow without changing
Dataset/Evaluation, general Vision runtime, authentication, or Village/World contracts.

## API contract

- `POST /api/photos`: authenticated multipart upload. `file` is required and `plantId` is optional.
  It returns `201 Created` with `photoId`, optional `plantId`, a relative authenticated-media path,
  and `uploadedAt`.
- `GET /api/photos/me`: returns only the current character's photo metadata.
- `POST /api/photos/{photoId}/recognize`: recognizes an already stored, owned photo.
- `POST /api/photos/{photoId}/recognize-with-image`: validates the supplied image and recognizes the
  owned photo.
- `GET /api/photos/recognitions`: returns only recognitions belonging to the current character.

No response exposes the filesystem storage root.

## File validation and storage

The server verifies image bytes rather than trusting the filename:

- an empty or unknown byte signature is rejected;
- declared MIME, detected byte signature, and extension must agree;
- formats supported by the configured ImageIO readers are decoded during validation, so corrupt
  input is rejected before filesystem or database persistence;
- HEIC/HEIF/AVIF/ICO signatures may be stored, but decoding remains deferred to a runtime with the
  required decoder;
- multipart limits are 15 MB per file and 20 MB per request.

Stored names use a generated UUID and canonical detected extension. The original filename is reduced
to its basename. Writes use a temporary sibling plus atomic move, and a database persistence failure
removes the newly stored file.

## Persistence and idempotency

Photo metadata is written after durable file storage. Recognition runs in one Spring transaction
covering classification, the unique recognition row, and its direct projections. The database
enforces one recognition per photo; a repeated sequential request returns the existing row and does
not duplicate persistence. Photo ownership is derived from the authenticated user and is checked
before recognition.

## Validation evidence

Targeted coverage includes valid plant/general uploads, empty and missing multipart data, MIME and
extension mismatch, corrupt bytes, storage-root traversal, storage failure cleanup, missing resources,
cross-user ownership denial, recognition persistence, fallback results, multipart recognition, and
sequential duplicate reuse.
