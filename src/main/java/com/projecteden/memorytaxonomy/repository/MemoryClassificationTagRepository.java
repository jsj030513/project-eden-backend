package com.projecteden.memorytaxonomy.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projecteden.memorytaxonomy.domain.MemoryClassificationTag;

public interface MemoryClassificationTagRepository
		extends JpaRepository<MemoryClassificationTag, Long> {

	List<MemoryClassificationTag> findAllByClassificationIdOrderByIdAsc(Long classificationId);

	boolean existsByClassificationIdAndTagId(Long classificationId, Long tagId);
}
