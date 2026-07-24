package com.projecteden.memorytaxonomy.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projecteden.memorytaxonomy.domain.MemoryClassification;

public interface MemoryClassificationRepository extends JpaRepository<MemoryClassification, Long> {

	List<MemoryClassification> findAllByPhotoIdOrderByCreatedAtDesc(Long photoId);

	Optional<MemoryClassification> findFirstByPhotoIdOrderByCreatedAtDesc(Long photoId);

	List<MemoryClassification> findAllByRecognitionIdOrderByCreatedAtDesc(Long recognitionId);

	Optional<MemoryClassification> findFirstByRecognitionIdAndProviderAndModelVersionAndTaxonomyVersion(
			Long recognitionId,
			String provider,
			String modelVersion,
			String taxonomyVersion);

	boolean existsByRecognitionIdAndProviderAndModelVersionAndTaxonomyVersion(
			Long recognitionId,
			String provider,
			String modelVersion,
			String taxonomyVersion);
}
