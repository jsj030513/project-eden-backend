package com.projecteden.ranking.domain;
import com.projecteden.user.domain.User;
import jakarta.persistence.*;
@Entity @Table(name = "rankings")
public class Ranking {
	@Id private Long userId;
	@OneToOne(fetch = FetchType.LAZY, optional = false) @MapsId @JoinColumn(name = "user_id") private User user;
	private int consecutiveLogins;
	private int totalPlayDays;
	private int seasonAchievements;
	protected Ranking() {}
	private Ranking(User user) { this.user = user; }
	public static Ranking create(User user) { return new Ranking(user); }
	public Long getUserId() { return userId; }
	public User getUser() { return user; }
	public int getConsecutiveLogins() { return consecutiveLogins; }
	public int getTotalPlayDays() { return totalPlayDays; }
	public int getSeasonAchievements() { return seasonAchievements; }
	public void updateMetrics(int consecutiveLogins, int totalPlayDays, int seasonAchievements) { this.consecutiveLogins = consecutiveLogins; this.totalPlayDays = totalPlayDays; this.seasonAchievements = seasonAchievements; }
}
