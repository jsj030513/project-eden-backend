package com.projecteden.memorytaxonomy.observation;

import java.util.Arrays;

import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

public final class UploadedImagePayload {

	private final String originalFileName;
	private final String contentType;
	private final long size;
	private final byte[] bytes;

	private UploadedImagePayload(
			String originalFileName,
			String contentType,
			long size,
			byte[] bytes) {
		this.originalFileName = originalFileName;
		this.contentType = contentType;
		this.size = size;
		this.bytes = bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
	}

	public static UploadedImagePayload from(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("인식할 사진 파일이 필요합니다.");
		}
		try {
			String fileName = StringUtils.cleanPath(
					file.getOriginalFilename() == null ? "photo" : file.getOriginalFilename());
			return new UploadedImagePayload(
					fileName,
					file.getContentType(),
					file.getSize(),
					file.getBytes());
		} catch (Exception ex) {
			throw new IllegalArgumentException("사진 파일을 읽을 수 없습니다.");
		}
	}

	public static UploadedImagePayload of(
			String originalFileName,
			String contentType,
			long size,
			byte[] bytes) {
		return new UploadedImagePayload(originalFileName, contentType, size, bytes);
	}

	public String originalFileName() {
		return originalFileName;
	}

	public String contentType() {
		return contentType;
	}

	public long size() {
		return size;
	}

	public boolean hasBytes() {
		return bytes != null && bytes.length > 0;
	}

	public byte[] bytes() {
		return bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
	}

	@Override
	public String toString() {
		return "UploadedImagePayload{" +
				"originalFileName='" + originalFileName + '\'' +
				", contentType='" + contentType + '\'' +
				", size=" + size +
				", bytesPresent=" + hasBytes() +
				'}';
	}
}
