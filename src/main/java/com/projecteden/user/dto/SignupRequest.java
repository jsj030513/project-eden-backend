package com.projecteden.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
		@NotBlank @Email String email,
		@NotBlank @Size(min = 8) String password,
		@NotBlank @Size(min = 2, max = 20) String nickname) {
}
