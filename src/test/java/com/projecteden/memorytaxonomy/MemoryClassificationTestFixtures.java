package com.projecteden.memorytaxonomy;

import java.util.UUID;

import com.projecteden.character.domain.Character;
import com.projecteden.character.domain.CharacterGender;
import com.projecteden.character.domain.CharacterJob;
import com.projecteden.character.domain.HairStyle;
import com.projecteden.character.domain.Outfit;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.photo.domain.Photo;
import com.projecteden.photo.repository.PhotoRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;

class MemoryClassificationTestFixtures {

	private final UserRepository users;
	private final CharacterRepository characters;
	private final PhotoRepository photos;

	MemoryClassificationTestFixtures(
			UserRepository users,
			CharacterRepository characters,
			PhotoRepository photos) {
		this.users = users;
		this.characters = characters;
		this.photos = photos;
	}

	Photo photo() {
		String suffix = UUID.randomUUID().toString();
		User user = users.save(new User(
				"classification-" + suffix + "@example.com",
				"password",
				"classification-" + suffix));
		Character character = characters.save(Character.create(
				user,
				"에덴",
				CharacterGender.NONE,
				HairStyle.PIXEL_CUT,
				"#000000",
				Outfit.BASIC,
				CharacterJob.BEGINNER));
		return photos.save(Photo.create(
				character,
				null,
				"memory.jpg",
				suffix + ".jpg",
				"image/jpeg",
				1024L,
				"/uploads/photos/" + suffix + ".jpg"));
	}
}
