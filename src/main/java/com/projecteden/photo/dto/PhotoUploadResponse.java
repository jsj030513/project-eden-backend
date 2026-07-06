package com.projecteden.photo.dto;

import java.time.LocalDateTime;

public record PhotoUploadResponse(
		Long id,
		Long plantId,
		String imageUrl,
		LocalDateTime uploadedAt) {
}
