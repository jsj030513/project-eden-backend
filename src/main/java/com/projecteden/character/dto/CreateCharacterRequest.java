package com.projecteden.character.dto;

import com.projecteden.character.domain.CharacterGender;
import com.projecteden.character.domain.CharacterJob;
import com.projecteden.character.domain.HairStyle;
import com.projecteden.character.domain.Outfit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCharacterRequest(
		@NotBlank @Size(min = 2, max = 20) String name,
		@NotNull CharacterGender gender,
		@NotNull HairStyle hairStyle,
		@NotBlank String hairColor,
		@NotNull Outfit outfit,
		@NotNull CharacterJob job) {
}
