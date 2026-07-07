package com.projecteden.statistics.domain;

import java.time.LocalDateTime;
import com.projecteden.character.domain.Character;
import jakarta.persistence.*;

@Entity @Table(name="character_statistics")
public class CharacterStatistics {
	@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
	@OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="character_id",nullable=false,unique=true) private Character character;
	private long totalDiscoveries; private long uniqueCollections; private long totalAchievements; private long totalTitles; private LocalDateTime lastDiscoveryAt; private LocalDateTime createdAt; private LocalDateTime updatedAt;
	protected CharacterStatistics(){} private CharacterStatistics(Character character){this.character=character;} public static CharacterStatistics create(Character character){return new CharacterStatistics(character);}
	public void refresh(long discoveries,long collections,long achievements,long titles,LocalDateTime last){this.totalDiscoveries=discoveries;this.uniqueCollections=collections;this.totalAchievements=achievements;this.totalTitles=titles;this.lastDiscoveryAt=last;}
	@PrePersist void prePersist(){LocalDateTime now=LocalDateTime.now();createdAt=now;updatedAt=now;} @PreUpdate void preUpdate(){updatedAt=LocalDateTime.now();}
	public Long getId(){return id;} public Character getCharacter(){return character;} public long getTotalDiscoveries(){return totalDiscoveries;} public long getUniqueCollections(){return uniqueCollections;} public long getTotalAchievements(){return totalAchievements;} public long getTotalTitles(){return totalTitles;} public LocalDateTime getLastDiscoveryAt(){return lastDiscoveryAt;}
}
