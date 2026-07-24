package com.projecteden.memorytaxonomy.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import com.projecteden.ai.domain.Recognition;
import com.projecteden.photo.domain.Photo;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Check(constraints = "confidence IS NULL OR (confidence >= 0 AND confidence <= 1)")
@Table(name = "memory_classifications")
public class MemoryClassification {

	public static final String DEFAULT_TAXONOMY_VERSION = "v1";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "photo_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Photo photo;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recognition_id")
	@OnDelete(action = OnDeleteAction.SET_NULL)
	private Recognition recognition;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "primary_category_id")
	private MemoryTaxonomyCategory primaryCategory;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private Map<String, Object> observation;

	@Column(length = 500)
	private String summary;

	@Column(precision = 5, scale = 4)
	private BigDecimal confidence;

	@Column(nullable = false, length = 40)
	private String provider;

	@Column(length = 100)
	private String modelVersion;

	@Column(nullable = false, length = 20)
	private String taxonomyVersion = DEFAULT_TAXONOMY_VERSION;

	@Column(nullable = false)
	private boolean fallback = false;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	@OneToMany(mappedBy = "classification", cascade = CascadeType.REMOVE, orphanRemoval = true)
	private List<MemoryClassificationCategory> secondaryCategories = new ArrayList<>();

	@OneToMany(mappedBy = "classification", cascade = CascadeType.REMOVE, orphanRemoval = true)
	private List<MemoryClassificationTag> tags = new ArrayList<>();

	protected MemoryClassification() {
	}

	private MemoryClassification(
			Photo photo,
			Recognition recognition,
			MemoryTaxonomyCategory primaryCategory,
			Map<String, Object> observation,
			String summary,
			BigDecimal confidence,
			String provider,
			String modelVersion,
			String taxonomyVersion,
			boolean fallback) {
		this.photo = photo;
		this.recognition = recognition;
		this.primaryCategory = primaryCategory;
		this.observation = observation;
		this.summary = summary;
		this.confidence = confidence;
		this.provider = provider;
		this.modelVersion = modelVersion;
		this.taxonomyVersion = taxonomyVersion;
		this.fallback = fallback;
	}

	public static MemoryClassification create(
			Photo photo,
			Recognition recognition,
			MemoryTaxonomyCategory primaryCategory,
			Map<String, Object> observation,
			String summary,
			BigDecimal confidence,
			String provider,
			String modelVersion,
			String taxonomyVersion,
			boolean fallback) {
		return new MemoryClassification(
				photo,
				recognition,
				primaryCategory,
				observation,
				summary,
				confidence,
				provider,
				modelVersion,
				taxonomyVersion,
				fallback);
	}

	public static MemoryClassification create(
			Photo photo,
			Recognition recognition,
			MemoryTaxonomyCategory primaryCategory,
			Map<String, Object> observation,
			String summary,
			BigDecimal confidence,
			String provider,
			String modelVersion,
			boolean fallback) {
		return create(
				photo,
				recognition,
				primaryCategory,
				observation,
				summary,
				confidence,
				provider,
				modelVersion,
				DEFAULT_TAXONOMY_VERSION,
				fallback);
	}

	public MemoryClassificationCategory addSecondaryCategory(
			MemoryTaxonomyCategory category,
			BigDecimal confidence) {
		if (category == null) {
			throw new IllegalArgumentException("분류 카테고리는 필수입니다.");
		}
		boolean alreadyExists = secondaryCategories.stream()
				.anyMatch(existing -> existing.hasCategory(category));
		if (alreadyExists) {
			throw new IllegalArgumentException("이미 추가된 분류 카테고리입니다.");
		}
		MemoryClassificationCategory classificationCategory =
				MemoryClassificationCategory.secondary(this, category, confidence);
		secondaryCategories.add(classificationCategory);
		return classificationCategory;
	}

	public MemoryClassificationTag addTag(MemoryTag tag, BigDecimal confidence) {
		if (tag == null) {
			throw new IllegalArgumentException("분류 태그는 필수입니다.");
		}
		boolean alreadyExists = tags.stream()
				.anyMatch(existing -> existing.hasTag(tag));
		if (alreadyExists) {
			throw new IllegalArgumentException("이미 추가된 분류 태그입니다.");
		}
		MemoryClassificationTag classificationTag =
				MemoryClassificationTag.create(this, tag, confidence);
		tags.add(classificationTag);
		return classificationTag;
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

	public Recognition getRecognition() {
		return recognition;
	}

	public MemoryTaxonomyCategory getPrimaryCategory() {
		return primaryCategory;
	}

	public Map<String, Object> getObservation() {
		return observation;
	}

	public String getSummary() {
		return summary;
	}

	public BigDecimal getConfidence() {
		return confidence;
	}

	public String getProvider() {
		return provider;
	}

	public String getModelVersion() {
		return modelVersion;
	}

	public String getTaxonomyVersion() {
		return taxonomyVersion;
	}

	public boolean isFallback() {
		return fallback;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public List<MemoryClassificationCategory> getSecondaryCategories() {
		return secondaryCategories;
	}

	public List<MemoryClassificationTag> getTags() {
		return tags;
	}
}
