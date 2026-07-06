package com.projecteden.daily.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.projecteden.character.domain.Character;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
		name = "daily_missions",
		uniqueConstraints = @UniqueConstraint(columnNames = {"character_id", "mission_date"}))
public class DailyMission {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "character_id", nullable = false)
	private Character character;

	@Column(name = "mission_date", nullable = false)
	private LocalDate missionDate;

	@Column(nullable = false)
	private boolean plantCompleted = false;

	@Column(nullable = false)
	private boolean harvestCompleted = false;

	@Column(nullable = false)
	private boolean photoCompleted = false;

	@Column(nullable = false)
	private boolean rewardClaimed = false;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	protected DailyMission() {
	}

	private DailyMission(Character character, LocalDate missionDate) {
		this.character = character;
		this.missionDate = missionDate;
	}

	public static DailyMission create(Character character, LocalDate missionDate) {
		return new DailyMission(character, missionDate);
	}

	public void completePlantMission() {
		plantCompleted = true;
	}

	public void completeHarvestMission() {
		harvestCompleted = true;
	}

	public void claimReward() {
		if (!plantCompleted || !harvestCompleted) {
			throw new IllegalArgumentException("일일 미션을 모두 완료해야 보상을 받을 수 있습니다.");
		}
		if (rewardClaimed) {
			throw new IllegalArgumentException("이미 일일 미션 보상을 수령했습니다.");
		}
		rewardClaimed = true;
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

	public LocalDate getMissionDate() {
		return missionDate;
	}

	public boolean isPlantCompleted() {
		return plantCompleted;
	}

	public boolean isHarvestCompleted() {
		return harvestCompleted;
	}

	public boolean isPhotoCompleted() {
		return photoCompleted;
	}

	public boolean isRewardClaimed() {
		return rewardClaimed;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
