package com.projecteden.vision.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.projecteden.vision.client.EdenVisionClient;
import com.projecteden.vision.dto.VisionClassifyResponse;
import com.projecteden.vision.exception.VisionRequestException;
import com.projecteden.vision.exception.VisionRequestException.Reason;

@Service
public class VisionService {
	private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

	private final boolean enabled;
	private final Optional<EdenVisionClient> client;

	public VisionService(@Value("${eden.vision-api.enabled:false}") boolean enabled,
			Optional<EdenVisionClient> client) {
		this.enabled = enabled;
		this.client = client;
	}

	public VisionClassifyResponse classify(MultipartFile image) {
		if (!enabled || client.isEmpty()) throw new VisionRequestException(Reason.DISABLED);
		if (image == null) throw new VisionRequestException(Reason.IMAGE_REQUIRED);
		if (image.isEmpty()) throw new VisionRequestException(Reason.IMAGE_EMPTY);
		if (image.getSize() > MAX_IMAGE_BYTES) throw new VisionRequestException(Reason.IMAGE_TOO_LARGE);
		// Decoding, pixel limits and format validation belong to Eden Vision, not PhotoUploadValidator.
		try {
			return client.orElseThrow().classify(image);
		} catch (IllegalArgumentException exception) {
			// The client can reject malformed multipart metadata. Never expose the supplied value.
			throw new VisionRequestException(Reason.INVALID_REQUEST);
		}
	}
}
