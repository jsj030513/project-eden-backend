package com.projecteden.memorytaxonomy.observation;

import java.util.Arrays;

import com.projecteden.imagenormalization.NormalizedImage;
import com.projecteden.photo.domain.Photo;

public final class ImageObservationRequest {

	private final Long photoId;
	private final String originalFileName;
	private final String contentType;
	private final long fileSize;
	private final byte[] imageBytes;
	private final Integer imageWidth;
	private final Integer imageHeight;
	private final String imageChecksum;
	private final boolean normalizedImage;

	private ImageObservationRequest(
			Long photoId,
			String originalFileName,
			String contentType,
			long fileSize,
			byte[] imageBytes, Integer imageWidth, Integer imageHeight, String imageChecksum, boolean normalizedImage) {
		this.photoId = photoId;
		this.originalFileName = originalFileName;
		this.contentType = contentType;
		this.fileSize = fileSize;
		this.imageBytes = imageBytes == null ? null : Arrays.copyOf(imageBytes, imageBytes.length);
		this.imageWidth = imageWidth;
		this.imageHeight = imageHeight;
		this.imageChecksum = imageChecksum;
		this.normalizedImage = normalizedImage;
	}

	public static ImageObservationRequest from(Photo photo) {
		return new ImageObservationRequest(
				photo.getId(),
				photo.getOriginalFileName(),
				photo.getContentType(),
				photo.getFileSize(),
				null, null, null, null, false);
	}

	public static ImageObservationRequest from(Photo photo, UploadedImagePayload payload) {
		if (payload == null) {
			return from(photo);
		}
		return new ImageObservationRequest(
				photo.getId(),
				payload.originalFileName() == null ? photo.getOriginalFileName() : payload.originalFileName(),
				payload.contentType() == null ? photo.getContentType() : payload.contentType(),
				payload.size(),
				payload.bytes(), null, null, null, false);
	}

	public static ImageObservationRequest from(Photo photo, NormalizedImage image) {
		byte[] bytes = image.bytes();
		return new ImageObservationRequest(
				photo.getId(),
				photo.getOriginalFileName(),
				image.contentType(),
				bytes.length,
				bytes, image.width(), image.height(), image.sha256(), true);
	}

	/**
	 * Creates a request from bytes that have already passed Project Eden's image
	 * normalization boundary. This is intentionally an internal provider contract;
	 * it does not change any public API DTO.
	 */
	public static ImageObservationRequest normalized(Long photoId, String originalFileName, NormalizedImage image) {
		byte[] bytes = image.bytes();
		return new ImageObservationRequest(
				photoId,
				originalFileName,
				image.contentType(),
				bytes.length,
				bytes,
				image.width(),
				image.height(),
				image.sha256(),
				true);
	}

	public static ImageObservationRequest of(
			Long photoId,
			String originalFileName,
			String contentType,
			long fileSize,
			byte[] imageBytes) {
		return new ImageObservationRequest(photoId, originalFileName, contentType, fileSize, imageBytes, null, null, null, false);
	}

	public Long photoId() {
		return photoId;
	}

	public String originalFileName() {
		return originalFileName;
	}

	public String contentType() {
		return contentType;
	}

	public long fileSize() {
		return fileSize;
	}

	public boolean hasImageBytes() {
		return imageBytes != null && imageBytes.length > 0;
	}

	public byte[] imageBytes() {
		return imageBytes == null ? null : Arrays.copyOf(imageBytes, imageBytes.length);
	}
	public Integer imageWidth() { return imageWidth; }
	public Integer imageHeight() { return imageHeight; }
	public String imageChecksum() { return imageChecksum; }
	public boolean isNormalizedImage() { return normalizedImage; }

	@Override
	public String toString() {
		return "ImageObservationRequest{" +
				"photoId=" + photoId +
				", originalFileName='" + originalFileName + '\'' +
				", contentType='" + contentType + '\'' +
				", fileSize=" + fileSize +
				", imageBytesPresent=" + hasImageBytes() +
				'}';
	}
}
