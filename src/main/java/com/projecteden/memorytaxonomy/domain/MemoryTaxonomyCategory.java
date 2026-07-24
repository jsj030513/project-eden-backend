package com.projecteden.memorytaxonomy.domain;

import java.time.LocalDateTime;

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
		name = "memory_taxonomy_categories",
		uniqueConstraints = @UniqueConstraint(columnNames = "code"))
public class MemoryTaxonomyCategory {

	public static final String DEFAULT_TAXONOMY_VERSION = "v1";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 80)
	private String code;

	@Column(nullable = false, length = 100)
	private String displayName;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_id")
	private MemoryTaxonomyCategory parent;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private MemoryTaxonomyCategoryType categoryType;

	@Column(nullable = false)
	private boolean active = true;

	@Column(nullable = false)
	private int sortOrder = 0;

	@Column(nullable = false, length = 20)
	private String taxonomyVersion = DEFAULT_TAXONOMY_VERSION;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	protected MemoryTaxonomyCategory() {
	}

	private MemoryTaxonomyCategory(
			String code,
			String displayName,
			MemoryTaxonomyCategory parent,
			MemoryTaxonomyCategoryType categoryType,
			int sortOrder,
			String taxonomyVersion) {
		this.code = code;
		this.displayName = displayName;
		this.parent = parent;
		this.categoryType = categoryType;
		this.sortOrder = sortOrder;
		this.taxonomyVersion = taxonomyVersion;
	}

	public static MemoryTaxonomyCategory create(
			String code,
			String displayName,
			MemoryTaxonomyCategoryType categoryType,
			int sortOrder) {
		return create(code, displayName, null, categoryType, sortOrder, DEFAULT_TAXONOMY_VERSION);
	}

	public static MemoryTaxonomyCategory create(
			String code,
			String displayName,
			MemoryTaxonomyCategory parent,
			MemoryTaxonomyCategoryType categoryType,
			int sortOrder,
			String taxonomyVersion) {
		return new MemoryTaxonomyCategory(
				code,
				displayName,
				parent,
				categoryType,
				sortOrder,
				taxonomyVersion);
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

	public MemoryTaxonomyCategory getParent() {
		return parent;
	}

	public MemoryTaxonomyCategoryType getCategoryType() {
		return categoryType;
	}

	public boolean isActive() {
		return active;
	}

	public int getSortOrder() {
		return sortOrder;
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
