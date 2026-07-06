package com.projecteden.world.repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import com.projecteden.world.domain.World;

public interface WorldRepository extends JpaRepository<World, Long> {

	Optional<World> findByCharacterId(Long characterId);

	@EntityGraph(attributePaths = "character")
	List<World> findByCharacterIdIn(Collection<Long> characterIds);

	boolean existsByCharacterId(Long characterId);
}
