package com.projecteden.notification.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.common.exception.ForbiddenOperationException;
import com.projecteden.notification.domain.Notification;
import com.projecteden.notification.domain.NotificationType;
import com.projecteden.notification.dto.NotificationResponse;
import com.projecteden.notification.repository.NotificationRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;

@Service
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final UserRepository userRepository;
	private final Clock clock;

	public NotificationService(
			NotificationRepository notificationRepository,
			UserRepository userRepository,
			Clock clock) {
		this.notificationRepository = notificationRepository;
		this.userRepository = userRepository;
		this.clock = clock;
	}

	@Transactional
	public void create(User user, NotificationType type, String message) {
		notificationRepository.save(Notification.create(user, type, message));
	}

	@Transactional
	public void createDailyPrompts() {
		LocalDate today = LocalDate.now(clock);
		LocalDateTime start = today.atStartOfDay();
		LocalDateTime end = today.plusDays(1).atStartOfDay();
		userRepository.findAll().stream()
				.filter(user -> !notificationRepository.existsByUserAndTypeAndCreatedAtBetween(
						user, NotificationType.DAILY_PROMPT, start, end))
				.forEach(user -> notificationRepository.save(Notification.create(
						user, NotificationType.DAILY_PROMPT, "오늘의 10초를 시작해 보세요.")));
	}

	@Transactional(readOnly = true)
	public List<NotificationResponse> getNotifications(Long userId) {
		User user = findUser(userId);
		return notificationRepository.findByUserOrderByCreatedAtDesc(user).stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional
	public NotificationResponse read(Long userId, Long notificationId) {
		Notification notification = notificationRepository.findById(notificationId)
				.orElseThrow(() -> new ResourceNotFoundException("알림을 찾을 수 없습니다."));
		if (!notification.getUser().getId().equals(userId)) {
			throw new ForbiddenOperationException("다른 사용자의 알림입니다.");
		}
		notification.markRead();
		return toResponse(notification);
	}

	private User findUser(Long id) {
		return userRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
	}

	private NotificationResponse toResponse(Notification notification) {
		return new NotificationResponse(
				notification.getId(),
				notification.getType(),
				notification.getMessage(),
				notification.getCreatedAt(),
				notification.isRead());
	}
}
