package com.projecteden.friend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.common.exception.ForbiddenOperationException;
import com.projecteden.friend.domain.Friend;
import com.projecteden.friend.domain.FriendStatus;
import com.projecteden.friend.dto.FriendRequestDTO;
import com.projecteden.friend.dto.FriendResponseDTO;
import com.projecteden.friend.repository.FriendRepository;
import com.projecteden.notification.domain.NotificationType;
import com.projecteden.notification.service.NotificationService;
import com.projecteden.profile.domain.Profile;
import com.projecteden.profile.repository.ProfileRepository;
import com.projecteden.ranking.domain.Ranking;
import com.projecteden.ranking.repository.RankingRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;

@Service
public class FriendService {

	private final FriendRepository friendRepository;
	private final UserRepository userRepository;
	private final ProfileRepository profileRepository;
	private final RankingRepository rankingRepository;
	private final NotificationService notificationService;
	private final Clock clock;

	public FriendService(
			FriendRepository friendRepository,
			UserRepository userRepository,
			ProfileRepository profileRepository,
			RankingRepository rankingRepository,
			NotificationService notificationService,
			Clock clock) {
		this.friendRepository = friendRepository;
		this.userRepository = userRepository;
		this.profileRepository = profileRepository;
		this.rankingRepository = rankingRepository;
		this.notificationService = notificationService;
		this.clock = clock;
	}

	@Transactional
	public FriendResponseDTO request(Long userId, FriendRequestDTO request) {
		User requester = findUser(userId);
		User receiver = findTarget(request);
		if (requester.getId().equals(receiver.getId())) {
			throw new IllegalArgumentException("자기 자신에게 친구 요청을 보낼 수 없습니다.");
		}
		if (existsEitherDirection(requester, receiver)) {
			throw new IllegalArgumentException("이미 친구이거나 요청이 진행 중입니다.");
		}
		return toResponse(friendRepository.save(Friend.create(requester, receiver)), receiver, null, null);
	}

	@Transactional
	public FriendResponseDTO accept(Long userId, Long requestId) {
		Friend friend = findFriend(requestId);
		if (!friend.getReceiver().getId().equals(userId)) {
			throw new ForbiddenOperationException("친구 요청을 수락할 권한이 없습니다.");
		}
		if (friend.getStatus() != FriendStatus.PENDING) {
			throw new IllegalArgumentException("이미 처리된 친구 요청입니다.");
		}
		friend.accept();
		createFriendAddedNotifications(friend);
		return toResponse(friend, friend.getRequester(), null, null);
	}

	@Transactional
	public void delete(Long userId, Long friendshipId) {
		Friend friend = findFriend(friendshipId);
		if (!friend.getRequester().getId().equals(userId)
				&& !friend.getReceiver().getId().equals(userId)) {
			throw new ForbiddenOperationException("친구 관계를 삭제할 권한이 없습니다.");
		}
		friendRepository.delete(friend);
	}

	@Transactional(readOnly = true)
	public List<FriendResponseDTO> list(Long userId) {
		User me = findUser(userId);
		return responses(friendRepository.findByRequesterOrReceiverAndStatus(
				me, me, FriendStatus.ACCEPTED), me, false);
	}

	@Transactional(readOnly = true)
	public List<FriendResponseDTO> pending(Long userId) {
		User me = findUser(userId);
		return responses(friendRepository.findByReceiverAndStatus(me, FriendStatus.PENDING), me, true);
	}

	@Transactional(readOnly = true)
	public boolean areFriends(User first, User second) {
		return friendRepository.existsAcceptedBetween(first, second);
	}

	private List<FriendResponseDTO> responses(List<Friend> friendships, User me, boolean pending) {
		List<User> users = friendships.stream()
				.map(friend -> pending ? friend.getRequester() : other(friend, me))
				.toList();
		List<Long> userIds = users.stream().map(User::getId).toList();
		Map<Long, Profile> profiles = profileRepository.findAllById(userIds).stream()
				.collect(Collectors.toMap(Profile::getUserId, Function.identity()));
		Map<Long, Ranking> rankings = rankingRepository.findAllById(userIds).stream()
				.collect(Collectors.toMap(Ranking::getUserId, Function.identity()));

		return java.util.stream.IntStream.range(0, friendships.size())
				.mapToObj(index -> toResponse(
						friendships.get(index),
						users.get(index),
						profiles.get(users.get(index).getId()),
						rankings.get(users.get(index).getId())))
				.toList();
	}

	private FriendResponseDTO toResponse(
			Friend friend, User user, Profile profile, Ranking ranking) {
		boolean onlineToday = user.getLastLoginAt() != null
				&& user.getLastLoginAt().toLocalDate().equals(LocalDate.now(clock));
		return new FriendResponseDTO(
				friend.getId(),
				user.getId(),
				user.getNickname(),
				profile == null ? null : profile.getAvatarUrl(),
				profile == null ? "SPRING" : profile.getCurrentSeason(),
				onlineToday,
				ranking == null ? 0 : ranking.getConsecutiveLogins(),
				user.getLastLoginAt(),
				friend.getStatus());
	}

	private void createFriendAddedNotifications(Friend friend) {
		notificationService.create(
				friend.getRequester(),
				NotificationType.FRIEND_ADDED,
				friend.getReceiver().getNickname() + "님과 친구가 되었습니다.");
		notificationService.create(
				friend.getReceiver(),
				NotificationType.FRIEND_ADDED,
				friend.getRequester().getNickname() + "님과 친구가 되었습니다.");
	}

	private boolean existsEitherDirection(User first, User second) {
		return friendRepository.existsByRequesterAndReceiver(first, second)
				|| friendRepository.existsByRequesterAndReceiver(second, first);
	}

	private User findTarget(FriendRequestDTO request) {
		if (request.nickname() != null && !request.nickname().isBlank()) {
			return userRepository.findByNickname(request.nickname())
					.orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
		}
		if (request.friendCode() == null || request.friendCode().isBlank()) {
			throw new IllegalArgumentException("friendCode 또는 nickname이 필요합니다.");
		}
		try {
			return findUser(Long.valueOf(request.friendCode()));
		} catch (NumberFormatException exception) {
			return userRepository.findByEmail(request.friendCode())
					.orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
		}
	}

	private Friend findFriend(Long id) {
		return friendRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("친구 요청을 찾을 수 없습니다."));
	}

	private User findUser(Long id) {
		return userRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
	}

	private User other(Friend friend, User me) {
		return friend.getRequester().getId().equals(me.getId())
				? friend.getReceiver()
				: friend.getRequester();
	}
}
