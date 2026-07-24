package com.projecteden.memorytaxonomy.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projecteden.memorytaxonomy.domain.MemoryClassificationCategory;

public interface MemoryClassificationCategoryRepository
		extends JpaRepository<MemoryClassificationCategory, Long> {

	List<MemoryClassificationCategory> findAllByClassificationIdOrderByIdAsc(
			Long classificationId);

	boolean existsByClassificationIdAndCategoryId(Long classificationId, Long categoryId);
}
