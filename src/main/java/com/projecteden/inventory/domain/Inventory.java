package com.projecteden.inventory.domain;

import java.time.LocalDateTime;

import com.projecteden.house.domain.House;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "inventories")
public class Inventory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "house_id", nullable = false, unique = true)
	private House house;

	@Column(nullable = false)
	private int capacity = 30;

	@Column(nullable = false)
	private int usedSlot = 0;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	protected Inventory() {
	}

	private Inventory(House house) {
		this.house = house;
	}

	public static Inventory create(House house) {
		return new Inventory(house);
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

	public House getHouse() {
		return house;
	}

	public int getCapacity() {
		return capacity;
	}

	public int getUsedSlot() {
		return usedSlot;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
