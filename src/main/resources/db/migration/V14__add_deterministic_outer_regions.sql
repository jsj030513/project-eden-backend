ALTER TABLE world_chunks
    DROP CONSTRAINT IF EXISTS world_chunks_region_type_check;

ALTER TABLE world_chunks
    ADD CONSTRAINT world_chunks_region_type_check
    CHECK (region_type IN ('HUB', 'MEADOW', 'FOREST', 'POND'));

ALTER TABLE world_chunks
    DROP CONSTRAINT IF EXISTS world_chunks_status_check;

ALTER TABLE world_chunks
    ADD CONSTRAINT world_chunks_status_check
    CHECK (status IN ('GENERATING', 'GENERATED', 'FAILED'));

UPDATE worlds
SET min_tile_x = -8,
    max_tile_x = 31,
    min_tile_y = -8,
    max_tile_y = 23,
    world_generation_version = 2
WHERE min_tile_x = 0
  AND max_tile_x = 23
  AND min_tile_y = 0
  AND max_tile_y = 15;
