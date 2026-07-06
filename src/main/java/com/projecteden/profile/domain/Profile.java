package com.projecteden.profile.domain;
import java.time.LocalDate;
import com.projecteden.user.domain.User;
import jakarta.persistence.*;
@Entity @Table(name = "profiles")
public class Profile {
	@Id private Long userId;
	@OneToOne(fetch = FetchType.LAZY, optional = false) @MapsId @JoinColumn(name = "user_id") private User user;
	private String avatarUrl;
	@Column(nullable = false) private LocalDate joinDate;
	private int totalPlayDays;
	private String currentSeason = "SPRING";
	private String representativeCreature;
	private String representativeIsland;
	protected Profile() {}
	private Profile(User user) { this.user = user; this.joinDate = LocalDate.now(); }
	public static Profile create(User user) { return new Profile(user); }
	public void update(String avatarUrl, String creature, String island) {
		if (avatarUrl != null) {
			this.avatarUrl = avatarUrl;
		}
		if (creature != null) {
			this.representativeCreature = creature;
		}
		if (island != null) {
			this.representativeIsland = island;
		}
	}
	public Long getUserId() { return userId; }
	public User getUser() { return user; }
	public String getAvatarUrl() { return avatarUrl; }
	public LocalDate getJoinDate() { return joinDate; }
	public int getTotalPlayDays() { return totalPlayDays; }
	public String getCurrentSeason() { return currentSeason; }
	public String getRepresentativeCreature() { return representativeCreature; }
	public String getRepresentativeIsland() { return representativeIsland; }
}
