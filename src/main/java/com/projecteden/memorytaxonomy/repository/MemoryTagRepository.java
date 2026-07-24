package com.projecteden.memorytaxonomy.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projecteden.memorytaxonomy.domain.MemoryTag;

public interface MemoryTagRepository extends JpaRepository<MemoryTag, Long> {

	Optional<MemoryTag> findByCode(String code);

	boolean existsByCode(String code);

	List<MemoryTag> findAllByActiveTrueOrderByCodeAsc();

	List<MemoryTag> findAllByTaxonomyVersionAndActiveTrueOrderByCodeAsc(String taxonomyVersion);
}
