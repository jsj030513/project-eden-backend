package com.projecteden.photo.storage;

import java.util.Locale;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.projecteden.imagenormalization.DetectedImageFormat;
import com.projecteden.imagenormalization.ImageFormat;
import com.projecteden.imagenormalization.ImageFormatDetector;
import com.projecteden.imagenormalization.ImageNormalizationException;
import com.projecteden.imagenormalization.ImageNormalizationService;
import com.projecteden.memorytaxonomy.observation.UploadedImagePayload;

@Component
public class PhotoUploadValidator {

	private final ImageFormatDetector imageFormatDetector;
	private final ImageNormalizationService imageNormalizationService;

	public PhotoUploadValidator(
			ImageFormatDetector imageFormatDetector,
			ImageNormalizationService imageNormalizationService) {
		this.imageFormatDetector = imageFormatDetector;
		this.imageNormalizationService = imageNormalizationService;
	}

	public ValidatedPhotoUpload validate(MultipartFile file) {
		UploadedImagePayload payload = UploadedImagePayload.from(file);
		DetectedImageFormat detected = imageFormatDetector.detect(payload);
		if (detected.format() == ImageFormat.UNKNOWN) {
			throw new IllegalArgumentException("지원하지 않거나 손상된 이미지 파일입니다.");
		}
		if (!detected.mimeMatched()) {
			throw new IllegalArgumentException("이미지 형식과 Content-Type이 일치하지 않습니다.");
		}
		if (!detected.extensionMatched()) {
			throw new IllegalArgumentException("이미지 형식과 파일 확장자가 일치하지 않습니다.");
		}
		validateDecodable(payload, detected.format());

		return new ValidatedPhotoUpload(
				safeOriginalFileName(payload.originalFileName()),
				detected.inferredMime(),
				canonicalExtension(detected.format()),
				payload.bytes());
	}

	private void validateDecodable(UploadedImagePayload payload, ImageFormat format) {
		if (format.isDecodeDeferred()) {
			return;
		}
		try {
			imageNormalizationService.normalize(payload);
		} catch (ImageNormalizationException exception) {
			throw new IllegalArgumentException(exception.getSafeMessage());
		}
	}

	private String safeOriginalFileName(String originalFileName) {
		String cleaned = StringUtils.cleanPath(originalFileName == null ? "photo" : originalFileName);
		String basename = StringUtils.getFilename(cleaned);
		return basename == null || basename.isBlank() ? "photo" : basename;
	}

	private String canonicalExtension(ImageFormat format) {
		return switch (format) {
			case JPEG -> "jpg";
			case TIFF -> "tiff";
			default -> format.name().toLowerCase(Locale.ROOT);
		};
	}
}
