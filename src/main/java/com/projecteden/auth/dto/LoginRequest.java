package com.projecteden.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import com.projecteden.user.service.EmailNormalizer;

public record LoginRequest(
		@NotBlank @Email String email,
		@NotBlank String password) {

	public LoginRequest {
		email = EmailNormalizer.normalize(email);
	}
}
