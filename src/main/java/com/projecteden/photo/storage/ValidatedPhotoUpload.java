package com.projecteden.photo.storage;

import java.util.Arrays;

public record ValidatedPhotoUpload(
		String originalFileName,
		String contentType,
		String extension,
		byte[] bytes) {

	public ValidatedPhotoUpload {
		bytes = Arrays.copyOf(bytes, bytes.length);
	}

	@Override
	public byte[] bytes() {
		return Arrays.copyOf(bytes, bytes.length);
	}

	public long size() {
		return bytes.length;
	}
}
