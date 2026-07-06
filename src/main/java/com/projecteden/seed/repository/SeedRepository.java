package com.projecteden.seed.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projecteden.seed.domain.Seed;
import com.projecteden.seed.domain.SeedType;

public interface SeedRepository extends JpaRepository<Seed, Long> {

	List<Seed> findByInventoryId(Long inventoryId);

	Optional<Seed> findByInventoryIdAndSeedType(Long inventoryId, SeedType seedType);
}
