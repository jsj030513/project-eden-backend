package com.projecteden.penalty.domain;
import java.time.LocalDateTime;
import com.projecteden.user.domain.User;
import jakarta.persistence.*;
@Entity @Table(name = "daily_penalties")
public class DailyPenalty {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
	@OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false, unique = true) private User user;
	private int missedDays;
	private LocalDateTime createdAt;
	protected DailyPenalty() {}
	private DailyPenalty(User user) { this.user = user; }
	public static DailyPenalty create(User user) { return new DailyPenalty(user); }
	@PrePersist void prePersist() { createdAt = LocalDateTime.now(); }
	public void updateMissedDays(int missedDays) { this.missedDays = Math.max(0, missedDays); }
	public Long getId() { return id; }
	public User getUser() { return user; }
	public int getMissedDays() { return missedDays; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}
