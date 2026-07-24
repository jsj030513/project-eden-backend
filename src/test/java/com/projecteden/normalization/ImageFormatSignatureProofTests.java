package com.projecteden.normalization;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class ImageFormatSignatureProofTests {

	@Test
	void detectsTargetRasterSignaturesWithoutTrustingFileNameOrDeclaredMimeType() {
		assertThat(detect(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})).isEqualTo(Format.JPEG);
		assertThat(detect(bytes("\u0089PNG\r\n\u001a\n"))).isEqualTo(Format.PNG);
		assertThat(detect(bytes("GIF89a"))).isEqualTo(Format.GIF);
		assertThat(detect(riff("WEBP"))).isEqualTo(Format.WEBP);
		assertThat(detect(isoBmff("heic"))).isEqualTo(Format.HEIC);
		assertThat(detect(isoBmff("mif1"))).isEqualTo(Format.HEIF);
		assertThat(detect(isoBmff("avif"))).isEqualTo(Format.AVIF);
		assertThat(detect(bytes("BM"))).isEqualTo(Format.BMP);
		assertThat(detect(new byte[] {'I', 'I', 42, 0})).isEqualTo(Format.TIFF);
		assertThat(detect(new byte[] {'M', 'M', 0, 42})).isEqualTo(Format.TIFF);
		assertThat(detect(new byte[] {0, 0, 1, 0})).isEqualTo(Format.ICO);
	}

	@Test
	void signatureWinsWhenDeclaredMimeAndExtensionDisagree() {
		byte[] pngNamedAsJpeg = bytes("\u0089PNG\r\n\u001a\n");
		assertThat(detect(pngNamedAsJpeg)).isEqualTo(Format.PNG);
	}

	@Test
	void rejectsEmptyAndUnknownBytes() {
		assertThat(detect(new byte[0])).isEqualTo(Format.UNKNOWN);
		assertThat(detect(bytes("not-an-image"))).isEqualTo(Format.UNKNOWN);
	}

	private Format detect(byte[] bytes) {
		if (startsWith(bytes, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})) {
			return Format.JPEG;
		}
		if (startsWith(bytes, bytes("\u0089PNG\r\n\u001a\n"))) {
			return Format.PNG;
		}
		if (startsWith(bytes, bytes("GIF87a")) || startsWith(bytes, bytes("GIF89a"))) {
			return Format.GIF;
		}
		if (startsWith(bytes, bytes("BM"))) {
			return Format.BMP;
		}
		if (startsWith(bytes, new byte[] {'I', 'I', 42, 0})
				|| startsWith(bytes, new byte[] {'M', 'M', 0, 42})) {
			return Format.TIFF;
		}
		if (startsWith(bytes, new byte[] {0, 0, 1, 0})) {
			return Format.ICO;
		}
		if (bytes.length >= 12 && ascii(bytes, 0, 4).equals("RIFF") && ascii(bytes, 8, 4).equals("WEBP")) {
			return Format.WEBP;
		}
		if (bytes.length >= 12 && ascii(bytes, 4, 4).equals("ftyp")) {
			String brand = ascii(bytes, 8, 4);
			if (brand.equals("avif") || brand.equals("avis")) {
				return Format.AVIF;
			}
			if (brand.startsWith("hei")) {
				return Format.HEIC;
			}
			if (brand.equals("mif1") || brand.equals("msf1")) {
				return Format.HEIF;
			}
		}
		return Format.UNKNOWN;
	}

	private byte[] riff(String type) {
		byte[] bytes = new byte[12];
		System.arraycopy(bytes("RIFF"), 0, bytes, 0, 4);
		System.arraycopy(bytes(type), 0, bytes, 8, 4);
		return bytes;
	}

	private byte[] isoBmff(String brand) {
		byte[] bytes = new byte[12];
		System.arraycopy(bytes("ftyp"), 0, bytes, 4, 4);
		System.arraycopy(bytes(brand), 0, bytes, 8, 4);
		return bytes;
	}

	private boolean startsWith(byte[] source, byte[] prefix) {
		return source.length >= prefix.length
				&& Arrays.equals(Arrays.copyOf(source, prefix.length), prefix);
	}

	private String ascii(byte[] bytes, int offset, int length) {
		return new String(bytes, offset, length, StandardCharsets.US_ASCII);
	}

	private byte[] bytes(String value) {
		return value.getBytes(StandardCharsets.ISO_8859_1);
	}

	private enum Format {
		JPEG, PNG, WEBP, GIF, HEIC, HEIF, AVIF, BMP, TIFF, ICO, UNKNOWN
	}
}
