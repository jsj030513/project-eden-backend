package com.projecteden.character.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.character.domain.Character;
import com.projecteden.character.dto.CharacterResponse;
import com.projecteden.character.dto.CreateCharacterRequest;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;

@Service
public class CharacterService {

	private final CharacterRepository characterRepository;
	private final UserRepository userRepository;

	public CharacterService(CharacterRepository characterRepository, UserRepository userRepository) {
		this.characterRepository = characterRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public CharacterResponse createCharacter(Long userId, CreateCharacterRequest request) {
		if (characterRepository.existsByUserId(userId)) {
			throw new IllegalArgumentException("이미 캐릭터가 존재합니다.");
		}

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
		Character character = Character.create(
				user,
				request.name(),
				request.gender(),
				request.hairStyle(),
				request.hairColor(),
				request.outfit(),
				request.job());

		return toResponse(characterRepository.save(character));
	}

	@Transactional(readOnly = true)
	public CharacterResponse getMyCharacter(Long userId) {
		Character character = characterRepository.findByUserId(userId)
				.orElseThrow(() -> new ResourceNotFoundException("캐릭터를 찾을 수 없습니다."));
		return toResponse(character);
	}

	private CharacterResponse toResponse(Character character) {
		return new CharacterResponse(
				character.getId(),
				character.getUser().getId(),
				character.getName(),
				character.getGender(),
				character.getHairStyle(),
				character.getHairColor(),
				character.getOutfit(),
				character.getJob(),
				character.getWeaponType(),
				character.getLevel(),
				character.getExp(),
				character.getEnergy());
	}
}
