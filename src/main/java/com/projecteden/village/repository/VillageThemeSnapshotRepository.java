package com.projecteden.village.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projecteden.village.domain.VillageThemeSnapshot;

public interface VillageThemeSnapshotRepository
		extends JpaRepository<VillageThemeSnapshot, Long> {

	Optional<VillageThemeSnapshot> findByCharacterId(Long characterId);

	boolean existsByCharacterId(Long characterId);
}
