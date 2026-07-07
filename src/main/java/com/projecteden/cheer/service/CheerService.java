package com.projecteden.cheer.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.character.domain.Character;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.cheer.domain.Cheer;
import com.projecteden.cheer.dto.CheerResponseDTO;
import com.projecteden.cheer.repository.CheerRepository;
import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.common.exception.ForbiddenOperationException;
import com.projecteden.friend.service.FriendService;
import com.projecteden.evolution.domain.EvolutionSourceType;
import com.projecteden.evolution.service.EvolutionService;
import com.projecteden.notification.domain.NotificationType;
import com.projecteden.notification.service.NotificationService;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;

@Service
public class CheerService {

	private static final int CHEER_EXPERIENCE = 5;

	private final CheerRepository cheerRepository;
	private final UserRepository userRepository;
	private final FriendService friendService;
	private final CharacterRepository characterRepository;
	private final NotificationService notificationService;
	private final Clock clock;
	private final EvolutionService evolutionService;

	public CheerService(
			CheerRepository cheerRepository,
			UserRepository userRepository,
			FriendService friendService,
			CharacterRepository characterRepository,
			NotificationService notificationService,
			Clock clock,
			EvolutionService evolutionService) {
		this.cheerRepository = cheerRepository;
		this.userRepository = userRepository;
		this.friendService = friendService;
		this.characterRepository = characterRepository;
		this.notificationService = notificationService;
		this.clock = clock;
		this.evolutionService = evolutionService;
	}

	@Transactional
	public CheerResponseDTO cheer(Long senderId, Long receiverId) {
		User sender = findUser(senderId);
		User receiver = findUser(receiverId);
		if (!friendService.areFriends(sender, receiver)) {
			throw new ForbiddenOperationException("친구에게만 응원할 수 있습니다.");
		}

		LocalDate today = LocalDate.now(clock);
		LocalDateTime start = today.atStartOfDay();
		LocalDateTime end = today.plusDays(1).atStartOfDay();
		if (cheerRepository.existsBySenderAndReceiverAndCheeredAtBetween(
				sender, receiver, start, end)) {
			throw new IllegalArgumentException("오늘 이미 이 친구를 응원했습니다.");
		}

		cheerRepository.save(Cheer.create(sender, receiver));
		Character character = characterRepository.findByUserId(receiver.getId())
				.orElseThrow(() -> new ResourceNotFoundException("캐릭터를 찾을 수 없습니다."));
		character.addExp(CHEER_EXPERIENCE);
		evolutionService.addEvolutionPoint(character.getId(), EvolutionSourceType.CHEER);
		notificationService.create(
				receiver,
				NotificationType.CHEER_RECEIVED,
				sender.getNickname() + "님이 응원을 보냈습니다.");
		return new CheerResponseDTO("친구에게 응원을 보냈습니다.", CHEER_EXPERIENCE);
	}

	private User findUser(Long id) {
		return userRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
	}
}
