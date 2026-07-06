package com.projecteden.resonance.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.projecteden.ai.domain.Recognition;
import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.character.domain.Character;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "resonances")
public class Resonance {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "character_id", nullable = false)
	private Character character;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recognition_id", nullable = false, unique = true)
	private Recognition recognition;

	@Enumerated(EnumType.STRING)
	@Column(name = "recognized_object", nullable = false)
	private RecognizedObject recognizedObject;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ResonanceRewardType rewardType;

	@Enumerated(EnumType.STRING)
	private SeedType rewardSeedType;

	@Column(nullable = false)
	private int rewardSeedQuantity;

	@Column(nullable = false)
	private int rewardGold;

	@Column(name = "resonance_date", nullable = false)
	private LocalDate resonanceDate;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	protected Resonance() {
	}

	private Resonance(
			Character character,
			Recognition recognition,
			RecognizedObject recognizedObject,
			ResonanceRewardType rewardType,
			SeedType rewardSeedType,
			int rewardSeedQuantity,
			int rewardGold,
			LocalDate resonanceDate) {
		this.character = character;
		this.recognition = recognition;
		this.recognizedObject = recognizedObject;
		this.rewardType = rewardType;
		this.rewardSeedType = rewardSeedType;
		this.rewardSeedQuantity = rewardSeedQuantity;
		this.rewardGold = rewardGold;
		this.resonanceDate = resonanceDate;
	}

	public static Resonance create(
			Character character,
			Recognition recognition,
			RecognizedObject recognizedObject,
			ResonanceRewardType rewardType,
			SeedType rewardSeedType,
			int rewardSeedQuantity,
			int rewardGold,
			LocalDate resonanceDate) {
		return new Resonance(
				character,
				recognition,
				recognizedObject,
				rewardType,
				rewardSeedType,
				rewardSeedQuantity,
				rewardGold,
				resonanceDate);
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

	public Recognition getRecognition() {
		return recognition;
	}

	public RecognizedObject getRecognizedObject() {
		return recognizedObject;
	}

	public ResonanceRewardType getRewardType() {
		return rewardType;
	}

	public SeedType getRewardSeedType() {
		return rewardSeedType;
	}

	public int getRewardSeedQuantity() {
		return rewardSeedQuantity;
	}

	public int getRewardGold() {
		return rewardGold;
	}

	public LocalDate getResonanceDate() {
		return resonanceDate;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
