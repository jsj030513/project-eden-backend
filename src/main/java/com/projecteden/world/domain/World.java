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

	public static final int DEFAULT_MIN_TILE_X = -8;
	public static final int DEFAULT_MAX_TILE_X = 31;
	public static final int DEFAULT_MIN_TILE_Y = -8;
	public static final int DEFAULT_MAX_TILE_Y = 23;
	public static final int FIXED_VILLAGE_GENERATION_VERSION = 3;

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

	@Column(name = "village_template_version", nullable = false)
	private int villageTemplateVersion = 0;

	@Column(name = "min_tile_x", nullable = false)
	private int minTileX = DEFAULT_MIN_TILE_X;

	@Column(name = "max_tile_x", nullable = false)
	private int maxTileX = DEFAULT_MAX_TILE_X;

	@Column(name = "min_tile_y", nullable = false)
	private int minTileY = DEFAULT_MIN_TILE_Y;

	@Column(name = "max_tile_y", nullable = false)
	private int maxTileY = DEFAULT_MAX_TILE_Y;

	@Column(name = "world_generation_version", nullable = false)
	private int worldGenerationVersion = FIXED_VILLAGE_GENERATION_VERSION;

	@Column(name = "last_animal_movement_at")
	private LocalDateTime lastAnimalMovementAt;

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

	public void addGold(int amount) {
		gold += amount;
	}

	public int getVillageTemplateVersion() { return villageTemplateVersion; }
	public void applyVillageTemplateVersion(int version) { villageTemplateVersion = Math.max(villageTemplateVersion, version); }
	public int getMinTileX() { return minTileX; }
	public int getMaxTileX() { return maxTileX; }
	public int getMinTileY() { return minTileY; }
	public int getMaxTileY() { return maxTileY; }
	public int getWorldGenerationVersion() { return worldGenerationVersion; }
	public boolean containsTile(int tileX, int tileY) {
		return tileX >= minTileX && tileX <= maxTileX && tileY >= minTileY && tileY <= maxTileY;
	}
	public LocalDateTime getLastAnimalMovementAt() { return lastAnimalMovementAt; }
	public void markAnimalMovement(LocalDateTime movedAt) { lastAnimalMovementAt = movedAt; }

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
