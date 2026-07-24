package com.projecteden.photo.dto;

import java.time.LocalDateTime;

public record PhotoResponse(
		Long photoId,
		Long plantId,
		String imageUrl,
		LocalDateTime uploadedAt) {
}
