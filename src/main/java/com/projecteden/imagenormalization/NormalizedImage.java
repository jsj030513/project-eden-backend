package com.projecteden.imagenormalization;

import java.util.Arrays;

public final class NormalizedImage {

	private final byte[] bytes;
	private final String contentType;
	private final ImageFormat format;
	private final int width;
	private final int height;
	private final ImageFormat originalFormat;
	private final int originalWidth;
	private final int originalHeight;
	private final boolean transformed;
	private final boolean orientationApplied;
	private final boolean resized;
	private final boolean colorNormalized;
	private final boolean alphaPreserved;
	private final boolean metadataStripped;
	private final String sha256;

	public NormalizedImage(
			byte[] bytes,
			String contentType,
			ImageFormat format,
			int width,
			int height,
			ImageFormat originalFormat,
			int originalWidth,
			int originalHeight,
			boolean transformed,
			boolean orientationApplied,
			boolean resized,
			boolean colorNormalized,
			boolean alphaPreserved,
			boolean metadataStripped,
			String sha256) {
		this.bytes = Arrays.copyOf(bytes, bytes.length);
		this.contentType = contentType;
		this.format = format;
		this.width = width;
		this.height = height;
		this.originalFormat = originalFormat;
		this.originalWidth = originalWidth;
		this.originalHeight = originalHeight;
		this.transformed = transformed;
		this.orientationApplied = orientationApplied;
		this.resized = resized;
		this.colorNormalized = colorNormalized;
		this.alphaPreserved = alphaPreserved;
		this.metadataStripped = metadataStripped;
		this.sha256 = sha256;
	}

	public byte[] bytes() { return Arrays.copyOf(bytes, bytes.length); }
	public String contentType() { return contentType; }
	public ImageFormat format() { return format; }
	public int width() { return width; }
	public int height() { return height; }
	public ImageFormat originalFormat() { return originalFormat; }
	public int originalWidth() { return originalWidth; }
	public int originalHeight() { return originalHeight; }
	public boolean transformed() { return transformed; }
	public boolean orientationApplied() { return orientationApplied; }
	public boolean resized() { return resized; }
	public boolean colorNormalized() { return colorNormalized; }
	public boolean alphaPreserved() { return alphaPreserved; }
	public boolean metadataStripped() { return metadataStripped; }
	public String sha256() { return sha256; }

	@Override
	public String toString() {
		return "NormalizedImage{" +
				"contentType='" + contentType + '\'' +
				", format=" + format +
				", width=" + width +
				", height=" + height +
				", originalFormat=" + originalFormat +
				", bytesPresent=" + (bytes.length > 0) +
				'}';
	}
}
