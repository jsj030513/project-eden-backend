package com.projecteden.village.domain;

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
@Table(name = "village_theme_snapshots")
public class VillageThemeSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "character_id", nullable = false, unique = true)
	private Character character;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private VillageTheme theme;

	@Enumerated(EnumType.STRING)
	private VillageCategory primaryCategory;

	@Enumerated(EnumType.STRING)
	private VillageCategory secondaryCategory;

	@Column(nullable = false)
	private String ruleVersion = "v1";

	@Column(nullable = false)
	private LocalDateTime appliedAt;

	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	protected VillageThemeSnapshot() {
	}

	private VillageThemeSnapshot(
			Character character,
			VillageTheme theme,
			VillageCategory primaryCategory,
			VillageCategory secondaryCategory,
			LocalDateTime appliedAt) {
		this.character = character;
		this.theme = theme;
		this.primaryCategory = primaryCategory;
		this.secondaryCategory = secondaryCategory;
		this.appliedAt = appliedAt;
	}

	public static VillageThemeSnapshot create(
			Character character,
			VillageTheme theme,
			VillageCategory primaryCategory,
			VillageCategory secondaryCategory,
			LocalDateTime appliedAt) {
		return new VillageThemeSnapshot(
				character, theme, primaryCategory, secondaryCategory, appliedAt);
	}

	public void updateCategories(
			VillageCategory primaryCategory, VillageCategory secondaryCategory) {
		this.primaryCategory = primaryCategory;
		this.secondaryCategory = secondaryCategory;
	}

	public void updateSecondaryCategory(VillageCategory secondaryCategory) {
		this.secondaryCategory = secondaryCategory;
	}

	public void changeTheme(
			VillageTheme theme,
			VillageCategory primaryCategory,
			VillageCategory secondaryCategory,
			LocalDateTime appliedAt) {
		this.theme = theme;
		this.primaryCategory = primaryCategory;
		this.secondaryCategory = secondaryCategory;
		this.appliedAt = appliedAt;
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

	public Long getId() { return id; }
	public Character getCharacter() { return character; }
	public VillageTheme getTheme() { return theme; }
	public VillageCategory getPrimaryCategory() { return primaryCategory; }
	public VillageCategory getSecondaryCategory() { return secondaryCategory; }
	public String getRuleVersion() { return ruleVersion; }
	public LocalDateTime getAppliedAt() { return appliedAt; }
}
