package com.projecteden.memorytaxonomy.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
		name = "memory_classification_tags",
		uniqueConstraints = @UniqueConstraint(columnNames = {"classification_id", "tag_id"}))
public class MemoryClassificationTag {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "classification_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private MemoryClassification classification;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tag_id", nullable = false)
	private MemoryTag tag;

	@Column(precision = 5, scale = 4)
	private BigDecimal confidence;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	protected MemoryClassificationTag() {
	}

	private MemoryClassificationTag(
			MemoryClassification classification,
			MemoryTag tag,
			BigDecimal confidence) {
		this.classification = classification;
		this.tag = tag;
		this.confidence = confidence;
	}

	public static MemoryClassificationTag create(
			MemoryClassification classification,
			MemoryTag tag,
			BigDecimal confidence) {
		return new MemoryClassificationTag(classification, tag, confidence);
	}

	boolean hasTag(MemoryTag tag) {
		if (this.tag == tag) {
			return true;
		}
		return this.tag != null
				&& tag != null
				&& this.tag.getId() != null
				&& this.tag.getId().equals(tag.getId());
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

	public MemoryTag getTag() {
		return tag;
	}

	public BigDecimal getConfidence() {
		return confidence;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
