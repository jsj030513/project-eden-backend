package com.projecteden.visit.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.character.domain.Character;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.friend.service.FriendService;
import com.projecteden.profile.domain.Profile;
import com.projecteden.profile.repository.ProfileRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.visit.domain.IslandVisit;
import com.projecteden.visit.dto.VisitResponseDTO;
import com.projecteden.visit.repository.IslandVisitRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;

@Service
public class IslandVisitService {

	private final IslandVisitRepository visitRepository;
	private final UserRepository userRepository;
	private final FriendService friendService;
	private final CharacterRepository characterRepository;
	private final WorldRepository worldRepository;
	private final ProfileRepository profileRepository;

	public IslandVisitService(
			IslandVisitRepository visitRepository,
			UserRepository userRepository,
			FriendService friendService,
			CharacterRepository characterRepository,
			WorldRepository worldRepository,
			ProfileRepository profileRepository) {
		this.visitRepository = visitRepository;
		this.userRepository = userRepository;
		this.friendService = friendService;
		this.characterRepository = characterRepository;
		this.worldRepository = worldRepository;
		this.profileRepository = profileRepository;
	}

	@Transactional
	public VisitResponseDTO visitIsland(Long userId, Long friendId) {
		User visitor = findUser(userId);
		User owner = findUser(friendId);
		if (!friendService.areFriends(visitor, owner)) {
			throw new IllegalArgumentException("친구의 섬만 방문할 수 있습니다.");
		}
		IslandVisit visit = visitRepository.save(IslandVisit.create(visitor, owner));
		return toResponse(
				visit,
				findWorld(owner.getId()),
				profileRepository.findByUser(owner).orElse(null));
	}

	@Transactional(readOnly = true)
	public List<VisitResponseDTO> history(Long userId) {
		List<IslandVisit> visits = visitRepository.findByVisitorOrderByVisitedAtDesc(findUser(userId));
		List<Long> ownerIds = visits.stream().map(visit -> visit.getOwner().getId()).distinct().toList();
		Map<Long, Character> characters = characterRepository.findByUserIdIn(ownerIds).stream()
				.collect(Collectors.toMap(character -> character.getUser().getId(), Function.identity()));
		Map<Long, World> worlds = worldRepository.findByCharacterIdIn(
				characters.values().stream().map(Character::getId).toList()).stream()
				.collect(Collectors.toMap(world -> world.getCharacter().getId(), Function.identity()));
		Map<Long, Profile> profiles = profileRepository.findAllById(ownerIds).stream()
				.collect(Collectors.toMap(Profile::getUserId, Function.identity()));

		return visits.stream().map(visit -> {
			Character character = characters.get(visit.getOwner().getId());
			World world = character == null ? null : worlds.get(character.getId());
			return toResponse(visit, world, profiles.get(visit.getOwner().getId()));
		}).toList();
	}

	private World findWorld(Long ownerId) {
		return characterRepository.findByUserId(ownerId)
				.flatMap(character -> worldRepository.findByCharacterId(character.getId()))
				.orElse(null);
	}

	private VisitResponseDTO toResponse(IslandVisit visit, World world, Profile profile) {
		return new VisitResponseDTO(
				visit.getId(),
				visit.getOwner().getId(),
				visit.getOwner().getNickname(),
				world == null ? null : world.getWorldName(),
				world == null ? "SPRING" : world.getSeason().name(),
				profile == null ? null : profile.getRepresentativeCreature(),
				profile == null ? null : profile.getRepresentativeIsland(),
				visit.getVisitedAt());
	}

	private User findUser(Long id) {
		return userRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
	}
}
