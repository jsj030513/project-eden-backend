package com.projecteden.world.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.projecteden.world.domain.World;

public interface WorldRepository extends JpaRepository<World, Long> {

	Optional<World> findByCharacterId(Long characterId);

	boolean existsByCharacterId(Long characterId);
}
