# Sprint 11 — Image Normalization Engine

## Goal

[Proposed] Normalize a supported raster upload into a bounded, metadata-stripped JPEG or PNG before it reaches `ImageObservationProvider`. Provider selection and world classification must not decide a file's format or decode it.

## Pre-implementation Upload Flow

The observations in this section describe the state at the start of Sprint 11. The later **STEP 2 Implementation Record** is the current normalization contract, and subsequent recovery work added atomic local photo-byte storage.

[Confirmed] `POST /api/photos` accepts a `MultipartFile`, rejects only an empty file, and stores metadata (`originalFileName`, declared `contentType`, file size, generated URL). It does not persist image bytes.

[Confirmed] `POST /api/photos/{photoId}/recognize-with-image` calls `UploadedImagePayload.from(file)`, which reads the multipart bytes and carries the declared content type and filename in request scope.

[Confirmed] `RecognitionApplicationService` creates `ImageObservationRequest` and sends it to the selected provider. `OpenAIImageObservationProvider` currently trusts its input `contentType` for its allow-list (`image/jpeg`, `image/png`, `image/webp`, `image/gif`) and creates a base64 data URL. `MockImageObservationProvider` can use the filename keyword rules.

[Confirmed] Spring multipart limits are 15 MB per file and 20 MB per request. The OpenAI handoff has a separate 10 MB byte limit.

[Confirmed] There is no production decoder, signature check, EXIF orientation correction, color conversion, resize step, or decoded-pixel resource limit yet.

## Supported Format Target

[Proposed] The target input set is JPEG/JPG, PNG, WEBP, GIF, HEIC/HEIF, AVIF, BMP, TIFF/TIF, and ICO. SVG, PDF, PSD, RAW camera files, video, and Live Photo packages remain outside this raster-normalization boundary.

## Format Capability Matrix

| Format | Signature detection | Current Java 21 proof | Recommended STEP 2 decoder | Native dependency | Orientation / alpha / animation | Output policy | Risk |
|---|---|---|---|---|---|---|---|
| JPEG | SOI `FF D8 FF` | [Confirmed] JDK decode proof passed | JDK ImageIO; TwelveMonkeys for extended cases | No | EXIF required; no alpha; no animation | JPEG | CMYK/ICC and malformed metadata |
| PNG | 8-byte PNG signature | [Confirmed] JDK decode proof passed | JDK ImageIO | No | no EXIF policy by default; alpha supported; no animation policy in MVP | PNG when alpha exists | large decoded bitmap |
| WEBP | RIFF + `WEBP` | [Confirmed] TwelveMonkeys 3.13.1 reader registered | TwelveMonkeys `imageio-webp` | No | alpha possible; animated WebP requires first-frame policy | PNG if alpha, otherwise JPEG | fixture-based real decode still required |
| GIF | `GIF87a` / `GIF89a` | [Confirmed] JDK decode proof passed | JDK ImageIO | No | palette transparency; animation supported by source but not MVP output | first frame to PNG/JPEG | frame count and animation bomb |
| HEIC | ISO-BMFF `ftyp` HEIC brand | [Confirmed] Signature proof only; no reader registered | No FOSS decoder selected | Not selected | EXIF/HEIF transform behavior requires a real fixture | Deferred | codec, patents, native portability |
| HEIF | ISO-BMFF `ftyp` compatible brand | [Confirmed] Signature proof only; no reader registered | No FOSS decoder selected | Not selected | same as HEIC | Deferred | generic container does not guarantee codec support |
| AVIF | ISO-BMFF `ftyp` AVIF brand | [Confirmed] Signature proof only; no reader registered | No FOSS decoder selected | Not selected | alpha and sequence behavior require a real fixture | Deferred | decoder availability and native classifiers |
| BMP | `BM` | [Confirmed] JDK decode proof passed | JDK ImageIO | No | no EXIF; alpha variant handling must be fixture-tested | PNG if alpha, otherwise JPEG | large dimensions |
| TIFF | little/big endian TIFF header | [Confirmed] JDK supports TIFF; TwelveMonkeys 3.13.1 reader registered | TwelveMonkeys `imageio-tiff` | No | multi-page, metadata, orientation need policy | first page to PNG/JPEG | compression, multi-page, decompression bomb |
| ICO | `00 00 01 00` | [Confirmed] Signature proof only; no reader in selected stack | Deferred; do not adopt the stale 2015 plugin | Not selected | may embed PNG/BMP and multiple sizes | Deferred | current maintained decoder not selected |

[Confirmed] The Java SE 21 ImageIO API specifies standard BMP, GIF, JPEG, PNG, TIFF, and WBMP plug-ins. TwelveMonkeys 3.13.1 advertises maintained TIFF and WebP modules and is discovered through ImageIO SPI. [Oracle ImageIO](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/javax/imageio/package-summary.html), [TwelveMonkeys formats](https://github.com/haraldk/TwelveMonkeys/blob/master/README.md).

## Dependency Comparison

| Candidate | Coverage relevant to Eden | Native | License / distribution | Decision |
|---|---|---|---|---|
| JDK ImageIO | JPEG, PNG, GIF, BMP, TIFF baseline | No | JDK | [Proposed] Core API for decode/encode and ImageReader dimension probing |
| TwelveMonkeys 3.13.1 | Extended JPEG/TIFF/BMP and WebP reader | No for selected modules | BSD 3-Clause; Maven Central | [Confirmed] Test-scope proof dependency for TIFF/WebP; promote only after STEP 2 integration tests |
| TwelveMonkeys legacy ICO 3.0.2 | ICO | No | BSD family; Maven Central latest is from 2015 | [Proposed] Reject for production due to stale release; no dependency added |
| JDeli ImageIO plugin | Vendor states HEIC/HEIF/AVIF/WebP in pure Java | No | Commercial evaluation/license decision required | [Unknown] Do not add without procurement, license review, and real-fixture proof |
| libheif / external process | HEIF and AVIF via native codecs | Yes | Codec and deployment review required | [Proposed] Do not add in STEP 2; evaluate only if product data shows HEIF/AVIF need |

[Confirmed] TwelveMonkeys publishes 3.13.1 and uses ImageIO SPI. Its project license is BSD. [TwelveMonkeys release/modules/license](https://github.com/haraldk/TwelveMonkeys/blob/master/README.md).

## Recommended Decoder Stack

[Proposed] STEP 2 should use `ImageIO` plus production-scoped TwelveMonkeys `imageio-tiff` and `imageio-webp`. The normalizer must use an `ImageInputStream`, select an `ImageReader` from stream content, probe width/height before `read`, dispose readers, and encode only with JDK JPEG/PNG writers.

[Proposed] HEIC, HEIF, AVIF, and ICO must return a typed `DECODER_UNAVAILABLE` result until a separately approved decoder passes fixtures on Mac ARM64, Linux x86_64, Linux ARM64, and CI. Signature detection still makes the rejection explicit and prevents MIME masquerading.

## Native Dependency Decision

[Confirmed] No native library is installed or selected. The current Dockerfile uses Maven Temurin 21 to build and Eclipse Temurin 21 JRE to run, with no system image-codec packages. The CI workflow runs Temurin 21 on `ubuntu-latest` and has no native setup. Docker Compose does not pin a CPU platform.

[Proposed] Keep STEP 2 pure Java. This works consistently for the selected stack on the current Mac ARM64 development host and Java CI. It avoids a Linux/macOS classifier matrix and Docker system-package change.

[Unknown] AWS production CPU architecture is not represented in this repository. Native HEIF/AVIF adoption requires an architecture decision, package inventory, Docker image proof, library-load fallback, and CI coverage before it can be accepted.

## Security Limits

[Confirmed] Current encoded ingress is 15 MB; current OpenAI provider handoff is 10 MB.

[Proposed] STEP 2 limits:

- encoded input: retain Spring's 15 MB maximum;
- normalizer provider output: 10 MB maximum so it remains valid for the current OpenAI handoff;
- maximum width/height: 8,192 pixels each;
- maximum decoded pixels: 24,000,000 (approximately 92 MiB for a four-byte raster before overhead);
- maximum source frames/pages examined: 1 for output, with a probe cap of 100 to reject unexpectedly large animations/documents;
- metadata: do not retain it in normalized output; bound metadata parsing through reader APIs and reject decoder failures;
- decode timeout: Java ImageIO has no reliable hard in-process cancellation guarantee. STEP 2 must not claim one; it should use pre-decode limits and a bounded executor policy, then document interruption limitations.

[Proposed] Decoder selection must require signature plus reader recognition. Declared MIME and filename extension are diagnostic only. A signature/MIME/extension mismatch is accepted only if the detected format is otherwise allowed; the normalized content type must use the detected/decoded format, never the declaration.

## EXIF Orientation Policy

[Proposed] Read orientation after a safe JPEG/TIFF metadata parse. Apply transform values 1–8 directly to pixels, then re-encode without orientation metadata. A parsing or transform failure must raise `NORMALIZATION_FAILED`, not silently rotate arbitrarily.

[Unknown] A copyright-safe fixture set covering all eight orientation values is not yet present. STEP 2 needs generated or public-domain binary fixtures and expected output dimensions/pixels.

## Color Space Policy

[Proposed] Output is sRGB. Convert supported RGB, grayscale, indexed, and embedded-ICC inputs to an explicit RGB/ARGB `BufferedImage` before encoding. Preserve alpha only in PNG.

[Proposed] CMYK JPEG or ICC conversion failure is `NORMALIZATION_FAILED`; do not substitute a visibly incorrect color conversion. Re-encoding strips source EXIF/ICC metadata after pixels are converted.

## Resize Policy

[Proposed] Use a configurable maximum dimension of 2,560 pixels after dimension/pixel validation and before encoding. Preserve aspect ratio; do not upscale. Use a Java2D quality interpolation policy first, then benchmark it before introducing another resampling dependency.

## Animation Policy

[Proposed] GIF and animated WebP normalize only frame zero in STEP 2. The service must record `transformed=true` and must not claim to preserve animation. Full animation processing is out of scope.

## Output Format Policy

[Proposed] A decoded image with alpha uses PNG. Without alpha it uses JPEG with configurable quality. The output has no copied filename, path, EXIF, ICC, or original metadata. A SHA-256 checksum can identify the normalized bytes without storing base64.

## Error Model

[Proposed] Use explicit categories: `UNSUPPORTED_FORMAT`, `SIGNATURE_MISMATCH`, `CORRUPTED_IMAGE`, `DECODER_UNAVAILABLE`, `RESOURCE_LIMIT_EXCEEDED`, and `NORMALIZATION_FAILED`. Do not expose a raw decoder exception or source bytes to API clients/logs.

## Proposed Architecture

```text
UploadedImagePayload
  -> ImageFormatDetector (signature first)
  -> ImageSecurityValidator (bytes, dimensions, pixels, frames)
  -> ImageDecoder (ImageIO reader chosen from stream)
  -> ImageOrientationNormalizer
  -> ImageColorNormalizer
  -> ImageResizer
  -> ImageEncoder (JPEG or PNG)
  -> NormalizedImage
  -> ImageObservationProvider
```

[Proposed] Keep `ImageNormalizationService.normalize(UploadedImagePayload)` as the public boundary. Use package-private helpers only where they make signature, decoder, and encoder policies independently testable. `NormalizedImage` must contain immutable bytes, content type, dimensions, original/detected format, transform flags, and checksum; its `toString` must exclude bytes and paths.

## Proof Test Results

[Confirmed] `ImageDecoderCapabilityProofTests` passed on Java 21 / macOS ARM64:

- actual JDK ImageIO encode/decode round-trips: JPEG, PNG, GIF, BMP;
- TwelveMonkeys 3.13.1 ImageIO reader registration: TIFF, WebP;
- no registered current-stack reader: HEIC, HEIF, AVIF, ICO.

[Confirmed] `ImageFormatSignatureProofTests` passed for JPEG, PNG, GIF, WebP, HEIC, HEIF, AVIF, BMP, TIFF (both endian signatures), ICO, unknown, and a PNG-signature/MIME-extension mismatch scenario.

[Confirmed] No binary fixture was added in STEP 1. The core decode proof generates tiny in-memory rasters. The next fixture manifest must include a public-domain or generated tiny sample per target format, PNG bytes named `.jpg`, text bytes named `.png`, truncated JPEG, oversized-dimension header, alpha PNG/WebP, animated GIF/WebP, CMYK JPEG, and EXIF orientation 1–8.

## Deployment Impact

[Confirmed] The proof dependencies are test-scoped only; production image handling and the packaged application are unchanged.

[Proposed] If selected modules are promoted in STEP 2, verify Spring Boot's repackaged jar preserves all `META-INF/services` entries; TwelveMonkeys relies on ImageIO SPI. A standard Spring Boot nested jar should be integration-tested rather than assumed.

[Proposed] Linux Docker and GitHub Actions should run the same pure-Java capability tests. A macOS ARM64-only success is not sufficient for a future native decoder decision.

## STEP 2 Implementation Boundary

[Proposed] Implement:

- `ImageFormatDetector`, `ImageNormalizationProperties`, `ImageNormalizationService`, `NormalizedImage`, and typed normalization exceptions;
- signature-first validation, ImageIO dimension/frame probe, byte/pixel limits, safe decoder disposal;
- JPEG/PNG/GIF/BMP/TIFF/WebP normalization using selected pure-Java decoders;
- EXIF orientation correction, sRGB conversion, resize, JPEG/PNG output, metadata stripping, checksum;
- generated/public-domain fixtures and malformed/mismatch/resource-limit tests.

[Proposed] Defer:

- HEIC/HEIF/AVIF/ICO decoding, native libraries, external processes, object storage, endpoint changes, OpenAI integration changes, database changes, and frontend changes.

[Proposed] Main risks: untrusted decoder inputs and decoded-memory pressure, Java ImageIO timeout limitations, color/orientation edge cases, and SPI packaging. No provider call should receive a normalized image until STEP 2 tests prove that boundary.

## Known Unsupported Cases

[Confirmed] Current selected stack does not decode HEIC, HEIF, AVIF, or ICO.

[Proposed] SVG, PDF, PSD, RAW camera images, video formats, and Live Photo packages are a separate Media Ingestion Sprint because they need different parser/render/frame/security policies.

## STEP 2 Implementation Record

### Added

- [Confirmed] `ImageFormatDetector`, `ImageNormalizationProperties`, `ImageNormalizationService`, `DefaultImageNormalizationService`, immutable `NormalizedImage`, and typed normalization errors.
- [Confirmed] Signature-first detection runs before decoding. Declared MIME type and file extension are diagnostic only, so a valid PNG named `.jpg` is normalized as PNG input rather than trusted as JPEG.
- [Confirmed] The normalizer probes dimensions and applicable GIF/WebP/TIFF frame/page counts with `ImageReader`, then decodes frame/page zero only.
- [Confirmed] Pixel orientation transforms are applied for EXIF values 1–8, source metadata is not copied to output, pixels are converted through `BufferedImage#getRGB` into sRGB RGB/ARGB, and output is bounded JPEG or PNG with SHA-256.

### Production Support

| Format | Status | Output / policy |
|---|---|---|
| JPEG, PNG, GIF, BMP | Supported | JPEG without alpha, PNG with alpha; GIF uses first frame |
| WebP, TIFF | Supported through TwelveMonkeys ImageIO SPI | JPEG without alpha, PNG with alpha; TIFF uses first page |
| HEIC, HEIF, AVIF, ICO | Signature detection only | Typed `DECODER_UNAVAILABLE`, then Recognition mock fallback |

### Security Limits

- [Confirmed] Encoded input: 15 MiB; normalized/provider output: 10 MiB; maximum dimensions: 8192×8192; decoded pixels: 24,000,000; frame/page probe: 20.
- [Confirmed] The request bytes, base64, full filename, EXIF, and GPS data are neither logged nor persisted by the normalization engine.
- [Confirmed] A malformed, unsupported, oversized, or otherwise failed normalization never passes the original uploaded bytes to the configured external provider. `RecognitionApplicationService` uses the existing deterministic mock fallback instead.

### Recognition Integration

[Confirmed] Only `POST /api/photos/{photoId}/recognize-with-image` has request bytes. It now follows:

```text
UploadedImagePayload → ImageNormalizationService → NormalizedImage
→ ImageObservationRequest → selected ImageObservationProvider
```

[Confirmed] `POST /api/photos/{photoId}/recognize` has no image bytes and keeps the existing metadata/filename-based fallback behavior. Recognition persistence, the legacy completion event, and STEP 4's single after-commit classification writer remain unchanged.

### Compatibility

- [Confirmed] No public endpoint, response DTO, database entity, migration, or frontend code changed.
- [Confirmed] The default observation provider is still mock.
- [Confirmed] TIFF/WebP readers are production-scoped pure-Java TwelveMonkeys modules; no native image library or system package was added.

### Known Limitations

1. [Confirmed] HEIC/HEIF/AVIF/ICO decode remains deferred despite signature detection.
2. [Confirmed] GIF/WebP animation and multi-page TIFF are normalized from their first frame/page only.
3. [Confirmed] Java ImageIO has no hard in-process decode timeout; this step limits encoded bytes, dimensions, pixels, and probed frame/page count before full decode.
4. [Inferred] Pixel-loop color conversion and downscaling avoid a macOS JVM `Graphics2D` abort observed during real normalization tests. The trade-off is nearest-neighbor shrinking rather than bicubic interpolation; image quality benchmarking belongs in STEP 3.

### STEP 3 Boundary

[Proposed] Evaluate maintained HEIC/HEIF/AVIF/ICO decoder strategies and image-quality/resampling requirements with cross-platform fixture tests before expanding the accepted input set or connecting an Eden Vision Core model.
