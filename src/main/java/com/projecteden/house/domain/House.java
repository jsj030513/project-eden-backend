package com.projecteden.house.domain;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "houses")
public class House {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "world_id", nullable = false, unique = true)
	private World world;

	@Column(nullable = false)
	private String houseName;

	@Column(nullable = false)
	private int level = 1;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private HouseType houseType = HouseType.CABIN;

	@Column(nullable = false)
	private int maxDecoration = 10;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	protected House() {
	}

	private House(World world, String houseName) {
		this.world = world;
		this.houseName = houseName;
	}

	public static House create(World world) {
		String characterName = world.getCharacter().getName();
		return new House(world, characterName + "의 집");
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

	public String getHouseName() {
		return houseName;
	}

	public int getLevel() {
		return level;
	}

	public HouseType getHouseType() {
		return houseType;
	}

	public int getMaxDecoration() {
		return maxDecoration;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
