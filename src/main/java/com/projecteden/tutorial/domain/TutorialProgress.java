package com.projecteden.tutorial.domain;

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
@Table(name = "tutorial_progress")
public class TutorialProgress {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "character_id", nullable = false, unique = true)
	private Character character;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TutorialStep currentStep = TutorialStep.WELCOME;

	@Column(nullable = false)
	private boolean completed = false;

	@Column(nullable = false)
	private LocalDateTime startedAt;

	private LocalDateTime completedAt;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	protected TutorialProgress() {
	}

	private TutorialProgress(Character character) {
		this.character = character;
	}

	public static TutorialProgress create(Character character) {
		return new TutorialProgress(character);
	}

	public void advanceTo(TutorialStep nextStep) {
		if (completed || nextStep.ordinal() != currentStep.ordinal() + 1) {
			throw new IllegalArgumentException("튜토리얼 단계를 순서대로 진행해야 합니다.");
		}

		currentStep = nextStep;
		if (nextStep == TutorialStep.FINISHED) {
			completed = true;
			completedAt = LocalDateTime.now();
		}
	}

	@PrePersist
	void prePersist() {
		LocalDateTime now = LocalDateTime.now();
		startedAt = now;
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

	public TutorialStep getCurrentStep() {
		return currentStep;
	}

	public boolean isCompleted() {
		return completed;
	}

	public LocalDateTime getStartedAt() {
		return startedAt;
	}

	public LocalDateTime getCompletedAt() {
		return completedAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
