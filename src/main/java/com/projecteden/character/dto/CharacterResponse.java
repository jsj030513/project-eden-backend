package com.projecteden.character.dto;

import com.projecteden.character.domain.CharacterGender;
import com.projecteden.character.domain.CharacterJob;
import com.projecteden.character.domain.HairStyle;
import com.projecteden.character.domain.Outfit;
import com.projecteden.character.domain.WeaponType;

public record CharacterResponse(
		Long id,
		Long userId,
		String name,
		CharacterGender gender,
		HairStyle hairStyle,
		String hairColor,
		Outfit outfit,
		CharacterJob job,
		WeaponType weaponType,
		int level,
		int exp,
		int energy) {
}
