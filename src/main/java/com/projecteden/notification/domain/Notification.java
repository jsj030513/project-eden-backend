package com.projecteden.notification.domain;
import java.time.LocalDateTime;
import com.projecteden.user.domain.User;
import jakarta.persistence.*;
@Entity @Table(name = "notifications")
public class Notification {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false) private User user;
	@Enumerated(EnumType.STRING) @Column(nullable = false) private NotificationType type;
	@Column(nullable = false, columnDefinition = "text") private String message;
	private LocalDateTime createdAt;
	@Column(name = "is_read", nullable = false) private boolean read;
	protected Notification() {}
	private Notification(User user, NotificationType type, String message) { this.user = user; this.type = type; this.message = message; }
	public static Notification create(User user, NotificationType type, String message) { return new Notification(user, type, message); }
	@PrePersist void prePersist() { createdAt = LocalDateTime.now(); }
	public void markRead() { read = true; }
	public Long getId() { return id; }
	public User getUser() { return user; }
	public NotificationType getType() { return type; }
	public String getMessage() { return message; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public boolean isRead() { return read; }
}
