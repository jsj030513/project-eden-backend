package com.projecteden.achievement.domain;
import java.time.LocalDateTime; import com.projecteden.character.domain.Character; import jakarta.persistence.*;
@Entity @Table(name="user_achievements",uniqueConstraints=@UniqueConstraint(columnNames={"character_id","achievement_id"}))
public class UserAchievement{
	@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="character_id",nullable=false) private Character character; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="achievement_id",nullable=false) private Achievement achievement; @Column(nullable=false) private LocalDateTime achievedAt; private LocalDateTime createdAt; private LocalDateTime updatedAt;
	protected UserAchievement(){} private UserAchievement(Character character,Achievement achievement,LocalDateTime now){this.character=character;this.achievement=achievement;this.achievedAt=now;} public static UserAchievement create(Character character,Achievement achievement,LocalDateTime now){return new UserAchievement(character,achievement,now);}
	@PrePersist void prePersist(){LocalDateTime now=LocalDateTime.now();createdAt=now;updatedAt=now;} @PreUpdate void preUpdate(){updatedAt=LocalDateTime.now();}
	public Long getId(){return id;} public Character getCharacter(){return character;} public Achievement getAchievement(){return achievement;} public LocalDateTime getAchievedAt(){return achievedAt;}
}
