package com.projecteden.profile.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.profile.domain.Profile;
import com.projecteden.profile.dto.ProfileResponseDTO;
import com.projecteden.profile.dto.ProfileUpdateDTO;
import com.projecteden.profile.repository.ProfileRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.world.repository.WorldRepository;

@Service
public class ProfileService {

	private final ProfileRepository profileRepository;
	private final UserRepository userRepository;
	private final CharacterRepository characterRepository;
	private final WorldRepository worldRepository;

	public ProfileService(
			ProfileRepository profileRepository,
			UserRepository userRepository,
			CharacterRepository characterRepository,
			WorldRepository worldRepository) {
		this.profileRepository = profileRepository;
		this.userRepository = userRepository;
		this.characterRepository = characterRepository;
		this.worldRepository = worldRepository;
	}

	@Transactional
	public ProfileResponseDTO get(Long userId) {
		User user = findUser(userId);
		Profile profile = profileRepository.findByUser(user)
				.orElseGet(() -> profileRepository.save(Profile.create(user)));
		return toResponse(profile);
	}

	@Transactional
	public ProfileResponseDTO update(Long userId, ProfileUpdateDTO request) {
		User user = findUser(userId);
		updateNickname(user, request.nickname());
		Profile profile = profileRepository.findByUser(user)
				.orElseGet(() -> profileRepository.save(Profile.create(user)));
		profile.update(
				request.avatarUrl(),
				request.representativeCreature(),
				request.representativeIsland());
		return toResponse(profile);
	}

	private void updateNickname(User user, String nickname) {
		if (nickname == null || nickname.isBlank() || nickname.equals(user.getNickname())) {
			return;
		}
		if (userRepository.existsByNickname(nickname)) {
			throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
		}
		user.updateNickname(nickname);
	}

	private User findUser(Long id) {
		return userRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
	}

	private ProfileResponseDTO toResponse(Profile profile) {
		var character = characterRepository.findByUserId(profile.getUserId()).orElse(null);
		var world = character == null
				? null
				: worldRepository.findByCharacterId(character.getId()).orElse(null);
		return new ProfileResponseDTO(
				profile.getUserId(),
				profile.getUser().getNickname(),
				profile.getAvatarUrl(),
				profile.getJoinDate(),
				profile.getTotalPlayDays(),
				world == null ? profile.getCurrentSeason() : world.getSeason().name(),
				profile.getRepresentativeCreature(),
				profile.getRepresentativeIsland());
	}
}
