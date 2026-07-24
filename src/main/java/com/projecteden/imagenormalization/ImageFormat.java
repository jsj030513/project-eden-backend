package com.projecteden.imagenormalization;

public enum ImageFormat {
	JPEG("image/jpeg"),
	PNG("image/png"),
	WEBP("image/webp"),
	GIF("image/gif"),
	BMP("image/bmp"),
	TIFF("image/tiff"),
	HEIC("image/heic"),
	HEIF("image/heif"),
	AVIF("image/avif"),
	ICO("image/x-icon"),
	UNKNOWN(null);

	private final String contentType;

	ImageFormat(String contentType) {
		this.contentType = contentType;
	}

	public String getContentType() {
		return contentType;
	}

	public boolean isDecodeDeferred() {
		return this == HEIC || this == HEIF || this == AVIF || this == ICO;
	}
}
