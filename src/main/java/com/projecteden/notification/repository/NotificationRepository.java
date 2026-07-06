package com.projecteden.notification.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.projecteden.notification.domain.Notification;
import com.projecteden.user.domain.User;
public interface NotificationRepository extends JpaRepository<Notification, Long> { List<Notification> findByUserOrderByCreatedAtDesc(User user); }
