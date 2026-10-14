package com.projecteden.world.repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.projecteden.world.domain.World;

public interface WorldRepository extends JpaRepository<World, Long> {

	Optional<World> findByCharacterId(Long characterId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select world from World world where world.character.id = :characterId")
	Optional<World> findByCharacterIdForUpdate(@Param("characterId") Long characterId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select world from World world where world.id = :worldId")
	Optional<World> findByIdForUpdate(@Param("worldId") Long worldId);

	@EntityGraph(attributePaths = "character")
	List<World> findByCharacterIdIn(Collection<Long> characterIds);

	boolean existsByCharacterId(Long characterId);
}
