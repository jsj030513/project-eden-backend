package com.projecteden.notification.service;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.projecteden.common.exception.ResourceNotFoundException;
import com.projecteden.notification.domain.*;
import com.projecteden.notification.dto.NotificationResponse;
import com.projecteden.notification.repository.NotificationRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
@Service
public class NotificationService {
	private final NotificationRepository repository; private final UserRepository users;
	public NotificationService(NotificationRepository repository, UserRepository users) { this.repository = repository; this.users = users; }
	@Transactional public void create(User user, NotificationType type, String message) { repository.save(Notification.create(user, type, message)); }
	@Scheduled(cron = "0 0 8 * * *") @Transactional public void createDailyPrompts() { users.findAll().forEach(u -> create(u, NotificationType.DAILY_PROMPT, "오늘의 10초를 시작해 보세요.")); }
	@Transactional(readOnly = true) public List<NotificationResponse> getNotifications(Long userId) { User u = user(userId); return repository.findByUserOrderByCreatedAtDesc(u).stream().map(this::response).toList(); }
	@Transactional public NotificationResponse read(Long userId, Long id) { Notification n = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("알림을 찾을 수 없습니다.")); if (!n.getUser().getId().equals(userId)) throw new IllegalArgumentException("다른 사용자의 알림입니다."); n.markRead(); return response(n); }
	private User user(Long id) { return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다.")); }
	private NotificationResponse response(Notification n) { return new NotificationResponse(n.getId(), n.getType(), n.getMessage(), n.getCreatedAt(), n.isRead()); }
}
