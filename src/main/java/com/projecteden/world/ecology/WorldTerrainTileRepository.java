package com.projecteden.world.ecology;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldTerrainTileRepository extends JpaRepository<WorldTerrainTile, Long> {
    List<WorldTerrainTile> findByCharacterIdOrderByYAscXAsc(Long characterId);
    List<WorldTerrainTile> findByCharacterIdAndXBetweenAndYBetweenOrderByYAscXAsc(
            Long characterId,
            int minX,
            int maxX,
            int minY,
            int maxY);
    Optional<WorldTerrainTile> findByCharacterIdAndXAndY(Long characterId, int x, int y);
    @Query("""
            select tile from WorldTerrainTile tile
            where tile.character.id = :characterId
              and (tile.x * 1000 + tile.y) in :coordinateKeys
            order by tile.y, tile.x
            """)
    List<WorldTerrainTile> findCandidateTiles(
            @Param("characterId") Long characterId,
            @Param("coordinateKeys") java.util.Collection<Integer> coordinateKeys);
    boolean existsByCharacterId(Long characterId);
    long countByCharacterIdAndXBetweenAndYBetween(
            Long characterId,
            int minX,
            int maxX,
            int minY,
            int maxY);
}
