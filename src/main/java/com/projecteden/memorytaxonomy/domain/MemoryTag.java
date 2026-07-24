package com.projecteden.memorytaxonomy.domain;

import java.time.LocalDateTime;

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
		name = "memory_tags",
		uniqueConstraints = @UniqueConstraint(columnNames = "code"))
public class MemoryTag {

	public static final String DEFAULT_TAXONOMY_VERSION = "v1";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 80)
	private String code;

	@Column(nullable = false, length = 100)
	private String displayName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private MemoryTagType tagType;

	@Column(nullable = false)
	private boolean active = true;

	@Column(nullable = false, length = 20)
	private String taxonomyVersion = DEFAULT_TAXONOMY_VERSION;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	protected MemoryTag() {
	}

	private MemoryTag(
			String code,
			String displayName,
			MemoryTagType tagType,
			String taxonomyVersion) {
		this.code = code;
		this.displayName = displayName;
		this.tagType = tagType;
		this.taxonomyVersion = taxonomyVersion;
	}

	public static MemoryTag create(String code, String displayName, MemoryTagType tagType) {
		return create(code, displayName, tagType, DEFAULT_TAXONOMY_VERSION);
	}

	public static MemoryTag create(
			String code,
			String displayName,
			MemoryTagType tagType,
			String taxonomyVersion) {
		return new MemoryTag(code, displayName, tagType, taxonomyVersion);
	}

	public void rename(String displayName) {
		this.displayName = displayName;
	}

	public void deactivate() {
		this.active = false;
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

	public String getCode() {
		return code;
	}

	public String getDisplayName() {
		return displayName;
	}

	public MemoryTagType getTagType() {
		return tagType;
	}

	public boolean isActive() {
		return active;
	}

	public String getTaxonomyVersion() {
		return taxonomyVersion;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
