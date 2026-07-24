package com.projecteden.imagenormalization;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.projecteden.memorytaxonomy.observation.UploadedImagePayload;

@Component
public class ImageFormatDetector {

	public DetectedImageFormat detect(UploadedImagePayload input) {
		byte[] bytes = input == null ? null : input.bytes();
		ImageFormat format = detect(bytes);
		String declaredMime = normalizeMime(input == null ? null : input.contentType());
		String extension = extension(input == null ? null : input.originalFileName());
		String inferredMime = format.getContentType();
		return new DetectedImageFormat(
				format,
				declaredMime,
				inferredMime,
				extension,
				format != ImageFormat.UNKNOWN,
				declaredMime != null && declaredMime.equals(inferredMime),
				extension != null && extensionMatches(format, extension));
	}

	public ImageFormat detect(byte[] bytes) {
		if (bytes == null || bytes.length == 0) {
			return ImageFormat.UNKNOWN;
		}
		if (startsWith(bytes, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})) {
			return ImageFormat.JPEG;
		}
		if (startsWith(bytes, new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'})) {
			return ImageFormat.PNG;
		}
		if (startsWith(bytes, ascii("GIF87a")) || startsWith(bytes, ascii("GIF89a"))) {
			return ImageFormat.GIF;
		}
		if (startsWith(bytes, ascii("BM"))) {
			return ImageFormat.BMP;
		}
		if (startsWith(bytes, new byte[] {'I', 'I', 42, 0})
				|| startsWith(bytes, new byte[] {'M', 'M', 0, 42})) {
			return ImageFormat.TIFF;
		}
		if (startsWith(bytes, new byte[] {0, 0, 1, 0})) {
			return ImageFormat.ICO;
		}
		if (bytes.length >= 12 && text(bytes, 0, 4).equals("RIFF") && text(bytes, 8, 4).equals("WEBP")) {
			return ImageFormat.WEBP;
		}
		if (bytes.length >= 12 && text(bytes, 4, 4).equals("ftyp")) {
			String brand = text(bytes, 8, 4);
			if (brand.equals("avif") || brand.equals("avis")) {
				return ImageFormat.AVIF;
			}
			if (brand.startsWith("hei")) {
				return ImageFormat.HEIC;
			}
			if (brand.equals("mif1") || brand.equals("msf1")) {
				return ImageFormat.HEIF;
			}
		}
		return ImageFormat.UNKNOWN;
	}

	private boolean startsWith(byte[] source, byte[] prefix) {
		return source.length >= prefix.length
				&& Arrays.equals(Arrays.copyOf(source, prefix.length), prefix);
	}

	private String text(byte[] bytes, int offset, int length) {
		return new String(bytes, offset, length, StandardCharsets.US_ASCII);
	}

	private byte[] ascii(String value) {
		return value.getBytes(StandardCharsets.US_ASCII);
	}

	private String normalizeMime(String contentType) {
		return contentType == null || contentType.isBlank()
				? null
				: contentType.trim().toLowerCase(Locale.ROOT);
	}

	private String extension(String fileName) {
		if (fileName == null || fileName.isBlank()) {
			return null;
		}
		int separator = fileName.lastIndexOf('.');
		return separator < 0 || separator == fileName.length() - 1
				? null
				: fileName.substring(separator + 1).toLowerCase(Locale.ROOT);
	}

	private boolean extensionMatches(ImageFormat format, String extension) {
		return switch (format) {
			case JPEG -> extension.equals("jpg") || extension.equals("jpeg");
			case TIFF -> extension.equals("tif") || extension.equals("tiff");
			default -> extension.equals(format.name().toLowerCase(Locale.ROOT));
		};
	}
}
