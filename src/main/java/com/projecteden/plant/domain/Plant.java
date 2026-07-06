package com.projecteden.plant.domain;

import java.time.LocalDateTime;

import com.projecteden.character.domain.Character;
import com.projecteden.region.domain.Region;
import com.projecteden.seed.domain.SeedType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "plants")
public class Plant {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "region_id", nullable = false)
	private Region region;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "character_id", nullable = false)
	private Character character;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SeedType seedType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PlantStage plantStage = PlantStage.SEED;

	@Column(nullable = false)
	private boolean resonanceBoosted = false;

	@Column(nullable = false)
	private LocalDateTime plantedAt;

	@Column(nullable = false)
	private LocalDateTime lastGrowthCheckedAt;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	protected Plant() {
	}

	private Plant(Region region, Character character, SeedType seedType, boolean resonanceBoosted) {
		this.region = region;
		this.character = character;
		this.seedType = seedType;
		this.resonanceBoosted = resonanceBoosted;
	}

	public static Plant create(
			Region region,
			Character character,
			SeedType seedType,
			boolean resonanceBoosted) {
		return new Plant(region, character, seedType, resonanceBoosted);
	}

	public void updateStage(PlantStage stage) {
		this.plantStage = stage;
	}

	public void touchGrowthCheckedAt(LocalDateTime checkedAt) {
		this.lastGrowthCheckedAt = checkedAt;
	}

	@PrePersist
	void prePersist() {
		LocalDateTime now = LocalDateTime.now();
		plantedAt = now;
		lastGrowthCheckedAt = now;
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

	public Region getRegion() {
		return region;
	}

	public Character getCharacter() {
		return character;
	}

	public SeedType getSeedType() {
		return seedType;
	}

	public PlantStage getPlantStage() {
		return plantStage;
	}

	public boolean isResonanceBoosted() {
		return resonanceBoosted;
	}

	public LocalDateTime getPlantedAt() {
		return plantedAt;
	}

	public LocalDateTime getLastGrowthCheckedAt() {
		return lastGrowthCheckedAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
