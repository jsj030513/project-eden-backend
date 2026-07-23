package com.projecteden.ai.domain;

import java.time.LocalDateTime;

import com.projecteden.photo.domain.Photo;
import com.projecteden.world.ecology.WorldPlacedObject;

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
@Table(name = "recognitions")
public class Recognition {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "photo_id", nullable = false, unique = true)
	private Photo photo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RecognizedObject recognizedObject;

	@Column(nullable = false)
	private int confidence;

	@Column(nullable = false)
	private boolean recognized;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "planting_target_object_id")
	private WorldPlacedObject plantingTargetObject;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	protected Recognition() {
	}

	private Recognition(
			Photo photo,
			RecognizedObject recognizedObject,
			int confidence,
			boolean recognized,
			WorldPlacedObject plantingTargetObject) {
		this.photo = photo;
		this.recognizedObject = recognizedObject;
		this.confidence = confidence;
		this.recognized = recognized;
		this.plantingTargetObject = plantingTargetObject;
	}

	public static Recognition create(
			Photo photo,
			RecognizedObject recognizedObject,
			int confidence,
			boolean recognized) {
		if (confidence < 0 || confidence > 100) {
			throw new IllegalArgumentException("인식 신뢰도는 0에서 100 사이여야 합니다.");
		}
		return new Recognition(photo, recognizedObject, confidence, recognized, null);
	}

	public static Recognition createForPlanting(
			Photo photo,
			RecognizedObject recognizedObject,
			int confidence,
			boolean recognized,
			WorldPlacedObject plantingTargetObject) {
		if (plantingTargetObject == null) {
			throw new IllegalArgumentException("심기 대상은 필수입니다.");
		}
		if (confidence < 0 || confidence > 100) {
			throw new IllegalArgumentException("인식 신뢰도는 0에서 100 사이여야 합니다.");
		}
		return new Recognition(photo, recognizedObject, confidence, recognized, plantingTargetObject);
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

	public Photo getPhoto() {
		return photo;
	}

	public RecognizedObject getRecognizedObject() {
		return recognizedObject;
	}

	public int getConfidence() {
		return confidence;
	}

	public boolean isRecognized() {
		return recognized;
	}

	public WorldPlacedObject getPlantingTargetObject() {
		return plantingTargetObject;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
