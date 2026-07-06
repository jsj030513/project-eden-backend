package com.projecteden.photo.dto;

import java.time.LocalDateTime;

public record PhotoResponse(
		Long id,
		String imageUrl,
		LocalDateTime uploadedAt) {
}
