package com.projecteden.world.domain;

import java.time.LocalDateTime;

import com.projecteden.character.domain.Character;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "worlds")
public class World {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "character_id", nullable = false, unique = true)
	private Character character;

	@Column(nullable = false)
	private String worldName;

	@Column(nullable = false)
	private long seed;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Season season = Season.SPRING;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Weather weather = Weather.SUNNY;

	@Column(name = "world_day", nullable = false)
	private int day = 1;

	@Column(nullable = false)
	private int gold = 100;

	@Column(nullable = false)
	private int wood = 20;

	@Column(nullable = false)
	private int stone = 10;

	@Column(nullable = false)
	private int food = 20;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	protected World() {
	}

	private World(Character character, String worldName, long seed) {
		this.character = character;
		this.worldName = worldName;
		this.seed = seed;
	}

	public static World create(Character character, long seed) {
		return new World(character, character.getName() + "의 세계", seed);
	}

	@PrePersist
	void prePersist() {
		LocalDateTime now = LocalDateTime.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		updatedAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public Character getCharacter() {
		return character;
	}

	public String getWorldName() {
		return worldName;
	}

	public long getSeed() {
		return seed;
	}

	public Season getSeason() {
		return season;
	}

	public Weather getWeather() {
		return weather;
	}

	public int getDay() {
		return day;
	}

	public int getGold() {
		return gold;
	}

	public int getWood() {
		return wood;
	}

	public int getStone() {
		return stone;
	}

	public int getFood() {
		return food;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
