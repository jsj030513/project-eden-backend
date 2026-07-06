package com.projecteden.photo.domain;

import java.time.LocalDateTime;

import com.projecteden.character.domain.Character;
import com.projecteden.plant.domain.Plant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "photos")
public class Photo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "character_id", nullable = false)
	private Character character;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "plant_id", unique = true)
	private Plant plant;

	@Column(nullable = false)
	private String originalFileName;

	@Column(nullable = false, unique = true)
	private String storedFileName;

	private String contentType;

	@Column(nullable = false)
	private long fileSize;

	@Column(nullable = false)
	private String imageUrl;

	@Column(nullable = false)
	private LocalDateTime uploadedAt;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	protected Photo() {
	}

	private Photo(
			Character character,
			Plant plant,
			String originalFileName,
			String storedFileName,
			String contentType,
			long fileSize,
			String imageUrl) {
		this.character = character;
		this.plant = plant;
		this.originalFileName = originalFileName;
		this.storedFileName = storedFileName;
		this.contentType = contentType;
		this.fileSize = fileSize;
		this.imageUrl = imageUrl;
	}

	public static Photo create(
			Character character,
			Plant plant,
			String originalFileName,
			String storedFileName,
			String contentType,
			long fileSize,
			String imageUrl) {
		return new Photo(
				character, plant, originalFileName, storedFileName, contentType, fileSize, imageUrl);
	}

	@PrePersist
	void prePersist() {
		LocalDateTime now = LocalDateTime.now();
		uploadedAt = now;
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

	public Plant getPlant() {
		return plant;
	}

	public String getOriginalFileName() {
		return originalFileName;
	}

	public String getStoredFileName() {
		return storedFileName;
	}

	public String getContentType() {
		return contentType;
	}

	public long getFileSize() {
		return fileSize;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public LocalDateTime getUploadedAt() {
		return uploadedAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
