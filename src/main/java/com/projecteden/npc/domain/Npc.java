package com.projecteden.npc.domain;

import java.time.LocalDateTime;

import com.projecteden.region.domain.Region;

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
		name = "npcs",
		uniqueConstraints = @UniqueConstraint(columnNames = {"region_id", "npc_type"}))
public class Npc {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "region_id", nullable = false)
	private Region region;

	@Enumerated(EnumType.STRING)
	@Column(name = "npc_type", nullable = false)
	private NpcType npcType;

	@Column(nullable = false)
	private String npcName;

	@Column(nullable = false)
	private String description;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	protected Npc() {
	}

	private Npc(Region region, NpcType npcType) {
		this.region = region;
		this.npcType = npcType;
		this.npcName = npcType.getNpcName();
		this.description = npcType.getDescription();
	}

	public static Npc create(Region region, NpcType npcType) {
		return new Npc(region, npcType);
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

	public Region getRegion() {
		return region;
	}

	public NpcType getNpcType() {
		return npcType;
	}

	public String getNpcName() {
		return npcName;
	}

	public String getDescription() {
		return description;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
