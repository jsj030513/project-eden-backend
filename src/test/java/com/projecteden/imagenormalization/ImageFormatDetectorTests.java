package com.projecteden.imagenormalization;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.projecteden.memorytaxonomy.observation.UploadedImagePayload;

class ImageFormatDetectorTests {

	private final ImageFormatDetector detector = new ImageFormatDetector();

	@Test
	void signatureHasPriorityOverDeclaredMimeAndExtension() {
		DetectedImageFormat result = detector.detect(UploadedImagePayload.of(
				"looks-like-jpeg.jpg", "image/jpeg", 8,
				new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}));

		assertThat(result.format()).isEqualTo(ImageFormat.PNG);
		assertThat(result.signatureMatched()).isTrue();
		assertThat(result.mimeMatched()).isFalse();
		assertThat(result.extensionMatched()).isFalse();
	}

	@Test
	void detectsEverySupportedAndDeferredSignature() {
		assertThat(detector.detect(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})).isEqualTo(ImageFormat.JPEG);
		assertThat(detector.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'})).isEqualTo(ImageFormat.PNG);
		assertThat(detector.detect(ascii("GIF89a"))).isEqualTo(ImageFormat.GIF);
		assertThat(detector.detect(riff("WEBP"))).isEqualTo(ImageFormat.WEBP);
		assertThat(detector.detect(ascii("BM"))).isEqualTo(ImageFormat.BMP);
		assertThat(detector.detect(new byte[] {'I', 'I', 42, 0})).isEqualTo(ImageFormat.TIFF);
		assertThat(detector.detect(new byte[] {'M', 'M', 0, 42})).isEqualTo(ImageFormat.TIFF);
		assertThat(detector.detect(isoBmff("heic"))).isEqualTo(ImageFormat.HEIC);
		assertThat(detector.detect(isoBmff("mif1"))).isEqualTo(ImageFormat.HEIF);
		assertThat(detector.detect(isoBmff("avif"))).isEqualTo(ImageFormat.AVIF);
		assertThat(detector.detect(new byte[] {0, 0, 1, 0})).isEqualTo(ImageFormat.ICO);
	}

	@Test
	void unknownAndEmptyInputRemainUnknown() {
		assertThat(detector.detect(new byte[0])).isEqualTo(ImageFormat.UNKNOWN);
		assertThat(detector.detect(ascii("not-an-image"))).isEqualTo(ImageFormat.UNKNOWN);
	}

	private byte[] riff(String type) {
		byte[] bytes = new byte[12];
		System.arraycopy(ascii("RIFF"), 0, bytes, 0, 4);
		System.arraycopy(ascii(type), 0, bytes, 8, 4);
		return bytes;
	}

	private byte[] isoBmff(String brand) {
		byte[] bytes = new byte[12];
		System.arraycopy(ascii("ftyp"), 0, bytes, 4, 4);
		System.arraycopy(ascii(brand), 0, bytes, 8, 4);
		return bytes;
	}

	private byte[] ascii(String value) {
		return value.getBytes(StandardCharsets.US_ASCII);
	}
}
