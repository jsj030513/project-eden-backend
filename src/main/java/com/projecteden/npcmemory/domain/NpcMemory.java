package com.projecteden.npcmemory.domain;

import java.time.LocalDateTime;

import com.projecteden.village.domain.VillageCategory;
import com.projecteden.village.domain.VillageTheme;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
		name = "npc_memories",
		uniqueConstraints = @UniqueConstraint(columnNames = {"character_id", "npc_id"}))
public class NpcMemory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "character_id", nullable = false)
	private Long characterId;

	@Column(name = "npc_id", nullable = false)
	private Long npcId;

	@Enumerated(EnumType.STRING)
	private VillageTheme rememberedTheme;

	@Enumerated(EnumType.STRING)
	private VillageCategory rememberedCategory;

	@Column(nullable = false)
	private int interactionCount = 0;

	private String lastDialogueKey;

	private LocalDateTime lastInteractedAt;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	protected NpcMemory() {
	}

	private NpcMemory(Long characterId, Long npcId) {
		this.characterId = characterId;
		this.npcId = npcId;
	}

	public static NpcMemory create(Long characterId, Long npcId) {
		return new NpcMemory(characterId, npcId);
	}

	public void recordInteraction(
			VillageTheme rememberedTheme,
			VillageCategory rememberedCategory,
			String lastDialogueKey,
			LocalDateTime lastInteractedAt) {
		this.interactionCount++;
		this.rememberedTheme = rememberedTheme;
		this.rememberedCategory = rememberedCategory;
		this.lastDialogueKey = lastDialogueKey;
		this.lastInteractedAt = lastInteractedAt;
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

	public Long getCharacterId() {
		return characterId;
	}

	public Long getNpcId() {
		return npcId;
	}

	public VillageTheme getRememberedTheme() {
		return rememberedTheme;
	}

	public VillageCategory getRememberedCategory() {
		return rememberedCategory;
	}

	public int getInteractionCount() {
		return interactionCount;
	}

	public String getLastDialogueKey() {
		return lastDialogueKey;
	}

	public LocalDateTime getLastInteractedAt() {
		return lastInteractedAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
