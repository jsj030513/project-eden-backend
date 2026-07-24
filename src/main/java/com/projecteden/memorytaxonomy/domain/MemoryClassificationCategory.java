package com.projecteden.memorytaxonomy.domain;

import java.math.BigDecimal;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Check(constraints = "confidence IS NULL OR (confidence >= 0 AND confidence <= 1)")
@Table(
		name = "memory_classification_categories",
		uniqueConstraints = @UniqueConstraint(columnNames = {"classification_id", "category_id"}))
public class MemoryClassificationCategory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "classification_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private MemoryClassification classification;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private MemoryTaxonomyCategory category;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MemoryClassificationCategoryRole role;

	@Column(precision = 5, scale = 4)
	private BigDecimal confidence;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	protected MemoryClassificationCategory() {
	}

	private MemoryClassificationCategory(
			MemoryClassification classification,
			MemoryTaxonomyCategory category,
			MemoryClassificationCategoryRole role,
			BigDecimal confidence) {
		this.classification = classification;
		this.category = category;
		this.role = role;
		this.confidence = confidence;
	}

	public static MemoryClassificationCategory create(
			MemoryClassification classification,
			MemoryTaxonomyCategory category,
			MemoryClassificationCategoryRole role,
			BigDecimal confidence) {
		return new MemoryClassificationCategory(classification, category, role, confidence);
	}

	public static MemoryClassificationCategory secondary(
			MemoryClassification classification,
			MemoryTaxonomyCategory category,
			BigDecimal confidence) {
		return create(
				classification,
				category,
				MemoryClassificationCategoryRole.SECONDARY,
				confidence);
	}

	boolean hasCategory(MemoryTaxonomyCategory category) {
		if (this.category == category) {
			return true;
		}
		return this.category != null
				&& category != null
				&& this.category.getId() != null
				&& this.category.getId().equals(category.getId());
	}

	@PrePersist
	void prePersist() {
		createdAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public MemoryClassification getClassification() {
		return classification;
	}

	public MemoryTaxonomyCategory getCategory() {
		return category;
	}

	public MemoryClassificationCategoryRole getRole() {
		return role;
	}

	public BigDecimal getConfidence() {
		return confidence;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
