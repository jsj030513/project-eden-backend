package com.projecteden.seed.domain;

import java.time.LocalDateTime;

import com.projecteden.inventory.domain.Inventory;

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
		name = "seeds",
		uniqueConstraints = @UniqueConstraint(columnNames = {"inventory_id", "seed_type"}))
public class Seed {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "inventory_id", nullable = false)
	private Inventory inventory;

	@Enumerated(EnumType.STRING)
	@Column(name = "seed_type", nullable = false)
	private SeedType seedType;

	@Column(nullable = false)
	private int quantity;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	protected Seed() {
	}

	private Seed(Inventory inventory, SeedType seedType, int quantity) {
		this.inventory = inventory;
		this.seedType = seedType;
		this.quantity = quantity;
	}

	public static Seed create(Inventory inventory, SeedType seedType, int quantity) {
		return new Seed(inventory, seedType, quantity);
	}

	public int useOne() {
		if (quantity <= 0) {
			throw new IllegalArgumentException("씨앗이 부족합니다.");
		}
		quantity--;
		return quantity;
	}

	public void addQuantity(int amount) {
		quantity += amount;
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

	public Inventory getInventory() {
		return inventory;
	}

	public SeedType getSeedType() {
		return seedType;
	}

	public int getQuantity() {
		return quantity;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
