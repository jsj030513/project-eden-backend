package com.projecteden.region.domain;

import java.time.LocalDateTime;

import com.projecteden.world.domain.World;

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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
		name = "regions",
		uniqueConstraints = @UniqueConstraint(columnNames = {"world_id", "region_type"}))
public class Region {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "world_id", nullable = false)
	private World world;

	@Enumerated(EnumType.STRING)
	@Column(name = "region_type", nullable = false)
	private RegionType regionType;

	@Column(nullable = false)
	private String displayName;

	@Column(nullable = false)
	private boolean unlocked = true;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	protected Region() {
	}

	private Region(World world, RegionType regionType) {
		this.world = world;
		this.regionType = regionType;
		this.displayName = regionType.getDisplayName();
	}

	public static Region create(World world, RegionType regionType) {
		return new Region(world, regionType);
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

	public World getWorld() {
		return world;
	}

	public RegionType getRegionType() {
		return regionType;
	}

	public String getDisplayName() {
		return displayName;
	}

	public boolean isUnlocked() {
		return unlocked;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
