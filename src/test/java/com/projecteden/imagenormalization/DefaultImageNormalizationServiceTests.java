package com.projecteden.imagenormalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.projecteden.memorytaxonomy.observation.UploadedImagePayload;

class DefaultImageNormalizationServiceTests {

	@Test
	void normalizesCoreRasterFormatsToJpegWhenNoAlpha() throws IOException {
		for (String format : new String[] {"jpeg", "gif", "bmp"}) {
			NormalizedImage result = service(defaultProperties()).normalize(payload(
					"sample." + format, "image/" + format, encode(rgbImage(), format)));

			assertThat(result.format()).isEqualTo(ImageFormat.JPEG);
			assertThat(result.contentType()).isEqualTo("image/jpeg");
			assertThat(result.width()).isEqualTo(6);
			assertThat(result.height()).isEqualTo(4);
			assertThat(result.sha256()).hasSize(64);
			assertThat(ImageIO.read(new java.io.ByteArrayInputStream(result.bytes()))).isNotNull();
		}
	}

	@Test
	void preservesAlphaAsPng() throws IOException {
		NormalizedImage result = service(defaultProperties()).normalize(payload(
				"transparent.png", "image/png", encode(alphaImage(), "png")));

		assertThat(result.format()).isEqualTo(ImageFormat.PNG);
		assertThat(result.contentType()).isEqualTo("image/png");
		assertThat(result.alphaPreserved()).isTrue();
		assertThat(ImageIO.read(new java.io.ByteArrayInputStream(result.bytes())).getColorModel().hasAlpha()).isTrue();
	}

	@Test
	void decodesWebpFixtureAndGeneratedTiff() throws IOException {
		NormalizedImage webp = service(defaultProperties()).normalize(payload(
				"small_1x1.webp", "application/octet-stream", fixture("normalization/small_1x1.webp")));
		NormalizedImage tiff = service(defaultProperties()).normalize(payload(
				"generated.tif", "image/tiff", encode(rgbImage(), "tiff")));

		assertThat(webp.originalFormat()).isEqualTo(ImageFormat.WEBP);
		assertThat(webp.format()).isEqualTo(ImageFormat.JPEG);
		assertThat(tiff.originalFormat()).isEqualTo(ImageFormat.TIFF);
		assertThat(tiff.format()).isIn(ImageFormat.JPEG, ImageFormat.PNG);
	}

	@Test
	void resizesWithoutUpscalingAndProducesStableChecksum() throws IOException {
		ImageNormalizationProperties properties = defaultProperties();
		properties.setMaxOutputWidth(4);
		properties.setMaxOutputHeight(4);
		byte[] source = encode(image(8, 4, BufferedImage.TYPE_INT_RGB), "png");

		NormalizedImage first = service(properties).normalize(payload("wide.png", "image/png", source));
		NormalizedImage second = service(properties).normalize(payload("wide.png", "image/png", source));

		assertThat(first.width()).isEqualTo(4);
		assertThat(first.height()).isEqualTo(2);
		assertThat(first.resized()).isTrue();
		assertThat(first.sha256()).isEqualTo(second.sha256());
	}

	@Test
	void rejectsDeferredAndInvalidImagesWithTypedErrors() {
		DefaultImageNormalizationService service = service(defaultProperties());

		assertThatThrownBy(() -> service.normalize(payload("phone.heic", "image/heic", isoBmff("heic"))))
				.isInstanceOf(ImageNormalizationException.class)
				.extracting(exception -> ((ImageNormalizationException) exception).getErrorCode())
				.isEqualTo(ImageNormalizationErrorCode.DECODER_UNAVAILABLE);
		assertThatThrownBy(() -> service.normalize(payload("broken.jpg", "image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})))
				.isInstanceOf(ImageNormalizationException.class)
				.extracting(exception -> ((ImageNormalizationException) exception).getErrorCode())
				.isEqualTo(ImageNormalizationErrorCode.CORRUPTED_IMAGE);
	}

	@Test
	void stopsOversizedEncodedInputBeforeDecoding() {
		ImageNormalizationProperties properties = defaultProperties();
		properties.setEncodedMaxBytes(3);

		assertThatThrownBy(() -> service(properties).normalize(payload("image.jpg", "image/jpeg",
				new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0})))
				.isInstanceOf(ImageNormalizationException.class)
				.extracting(exception -> ((ImageNormalizationException) exception).getErrorCode())
				.isEqualTo(ImageNormalizationErrorCode.ENCODED_SIZE_EXCEEDED);
	}

	private DefaultImageNormalizationService service(ImageNormalizationProperties properties) {
		return new DefaultImageNormalizationService(new ImageFormatDetector(), properties);
	}

	private ImageNormalizationProperties defaultProperties() {
		return new ImageNormalizationProperties();
	}

	private UploadedImagePayload payload(String fileName, String contentType, byte[] bytes) {
		return UploadedImagePayload.of(fileName, contentType, bytes.length, bytes);
	}

	private BufferedImage rgbImage() {
		return image(6, 4, BufferedImage.TYPE_INT_RGB);
	}

	private BufferedImage alphaImage() {
		BufferedImage image = image(6, 4, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, 0x00000000);
		return image;
	}

	private BufferedImage image(int width, int height, int type) {
		BufferedImage image = new BufferedImage(width, height, type);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setRGB(x, y, new Color(x * 20, y * 30, 120).getRGB());
			}
		}
		return image;
	}

	private byte[] encode(BufferedImage image, String format) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		assertThat(ImageIO.write(image, format, output)).isTrue();
		return output.toByteArray();
	}

	private byte[] fixture(String path) throws IOException {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
			assertThat(input).as("fixture %s", path).isNotNull();
			return input.readAllBytes();
		}
	}

	private byte[] isoBmff(String brand) {
		byte[] bytes = new byte[12];
		System.arraycopy("ftyp".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, bytes, 4, 4);
		System.arraycopy(brand.getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, bytes, 8, 4);
		return bytes;
	}
}
