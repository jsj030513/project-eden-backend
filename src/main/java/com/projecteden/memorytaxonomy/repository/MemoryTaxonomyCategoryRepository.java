package com.projecteden.memorytaxonomy.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projecteden.memorytaxonomy.domain.MemoryTaxonomyCategory;

public interface MemoryTaxonomyCategoryRepository extends JpaRepository<MemoryTaxonomyCategory, Long> {

	Optional<MemoryTaxonomyCategory> findByCode(String code);

	boolean existsByCode(String code);

	List<MemoryTaxonomyCategory> findAllByActiveTrueOrderBySortOrderAscIdAsc();

	List<MemoryTaxonomyCategory> findAllByTaxonomyVersionAndActiveTrueOrderBySortOrderAscIdAsc(
			String taxonomyVersion);
}
