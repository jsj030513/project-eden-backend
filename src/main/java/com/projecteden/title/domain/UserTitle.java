package com.projecteden.title.domain;
import java.time.LocalDateTime; import com.projecteden.character.domain.Character; import jakarta.persistence.*;
@Entity @Table(name="user_titles",uniqueConstraints=@UniqueConstraint(columnNames={"character_id","title_id"})) public class UserTitle{
	@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="character_id",nullable=false) private Character character; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="title_id",nullable=false) private Title title; @Column(nullable=false) private boolean active; @Column(nullable=false) private LocalDateTime acquiredAt; private LocalDateTime createdAt; private LocalDateTime updatedAt;
	protected UserTitle(){} private UserTitle(Character character,Title title,LocalDateTime now){this.character=character;this.title=title;this.acquiredAt=now;} public static UserTitle create(Character character,Title title,LocalDateTime now){return new UserTitle(character,title,now);} public void activate(){active=true;} public void deactivate(){active=false;}
	@PrePersist void prePersist(){LocalDateTime now=LocalDateTime.now();createdAt=now;updatedAt=now;} @PreUpdate void preUpdate(){updatedAt=LocalDateTime.now();}
	public Long getId(){return id;} public Character getCharacter(){return character;} public Title getTitle(){return title;} public boolean isActive(){return active;} public LocalDateTime getAcquiredAt(){return acquiredAt;}
}
