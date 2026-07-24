# Normalization Test Fixture

`small_1x1.webp` is the 1×1 WebP decoder test vector from the
[WebM libwebp test-data repository](https://chromium.googlesource.com/webm/libwebp-test-data/+/refs/heads/main/small_1x1.webp).
It is redistributed under the WebM software license used by libwebp.

- Purpose: prove that the production ImageIO WebP reader decodes a real WebP bitstream.
- SHA-256: `2f34799482dd5349b549d113fdaa188714d9737fe414e71541b752627bedbde3`
- Personal or application data: none.

Other raster fixtures are generated in memory by the tests so that source metadata
and host-specific paths cannot enter the repository.
