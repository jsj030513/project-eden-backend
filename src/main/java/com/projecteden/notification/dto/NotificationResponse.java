package com.projecteden.notification.dto;
import java.time.LocalDateTime;
import com.projecteden.notification.domain.NotificationType;
public record NotificationResponse(Long id, NotificationType type, String message, LocalDateTime createdAt, boolean read) {}
