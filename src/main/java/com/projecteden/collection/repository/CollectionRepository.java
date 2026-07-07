package com.projecteden.collection.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.collection.domain.Collection;

public interface CollectionRepository extends JpaRepository<Collection, Long> {
	Optional<Collection> findByCharacterIdAndRecognizedObject(Long characterId, RecognizedObject recognizedObject);
	List<Collection> findByCharacterId(Long characterId);
	long countByCharacterId(Long characterId);
	@Query("select coalesce(sum(c.discoveredCount), 0) from Collection c where c.character.id = :characterId")
	long sumDiscoveredCountByCharacterId(@Param("characterId") Long characterId);
	@Query("select coalesce(max(c.discoveredCount), 0) from Collection c where c.character.id = :characterId")
	int maxDiscoveredCountByCharacterId(@Param("characterId") Long characterId);
	Optional<Collection> findTopByCharacterIdOrderByLastDiscoveredAtDesc(Long characterId);
}
