ALTER TABLE worlds
    ADD COLUMN min_tile_x INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN max_tile_x INTEGER NOT NULL DEFAULT 23,
    ADD COLUMN min_tile_y INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN max_tile_y INTEGER NOT NULL DEFAULT 15,
    ADD COLUMN world_generation_version INTEGER NOT NULL DEFAULT 1;

ALTER TABLE worlds
    ADD CONSTRAINT worlds_tile_bounds_check
    CHECK (min_tile_x <= max_tile_x AND min_tile_y <= max_tile_y);

CREATE INDEX idx_world_terrain_tiles_character_y_x
    ON world_terrain_tiles (character_id, tile_y, tile_x);

CREATE INDEX idx_world_changes_character_id_id
    ON world_changes (character_id, id);

CREATE INDEX idx_world_placed_objects_position
    ON world_placed_objects (positionx, positiony, world_change_id);
