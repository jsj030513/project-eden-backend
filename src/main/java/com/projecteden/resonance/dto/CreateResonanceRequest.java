package com.projecteden.resonance.dto;

import jakarta.validation.constraints.NotNull;

public record CreateResonanceRequest(
		@NotNull(message = "Recognition ID는 필수입니다.") Long recognitionId) {
}
