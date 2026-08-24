package com.projecteden.world.ecology;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Persists one deterministic terrain set through JDBC batching. The caller
 * holds the world row lock and supplies only missing coordinates, so this is
 * portable across PostgreSQL and the H2 test profile without vendor SQL.
 */
@Component
public class WorldTerrainBatchWriter {
    private final JdbcTemplate jdbc;

    public WorldTerrainBatchWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertMissing(Long characterId, List<TerrainSeed> seeds) {
        if (seeds.isEmpty()) return;
        String sql = """
                insert into world_terrain_tiles
                    (character_id, tile_x, tile_y, terrain_type, walkable)
                values (?, ?, ?, ?, ?)
                """;
        jdbc.batchUpdate(sql, seeds, Math.min(128, seeds.size()), (statement, seed) -> {
            statement.setLong(1, characterId);
            statement.setInt(2, seed.x());
            statement.setInt(3, seed.y());
            statement.setString(4, seed.terrainType().name());
            statement.setBoolean(5, seed.terrainType().isLandWalkable());
        });
    }

    public record TerrainSeed(int x, int y, TerrainType terrainType) { }
}
