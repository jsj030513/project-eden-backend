ALTER TABLE world_changes
    ADD COLUMN ecology_profile_key VARCHAR(96),
    ADD COLUMN ecology_category VARCHAR(32),
    ADD COLUMN placement_applied BOOLEAN,
    ADD COLUMN placement_region_type VARCHAR(32),
    ADD COLUMN placement_chunk_x INTEGER,
    ADD COLUMN placement_chunk_y INTEGER,
    ADD COLUMN spawn_zone_tag VARCHAR(64),
    ADD COLUMN placement_version INTEGER,
    ADD COLUMN placement_reason VARCHAR(64),
    ADD COLUMN placed_at TIMESTAMP;

UPDATE world_changes change
SET ecology_profile_key = 'LEGACY',
    placement_applied = EXISTS (
        SELECT 1 FROM world_placed_objects object WHERE object.world_change_id = change.id
    ),
    placement_version = 0,
    placement_reason = CASE
        WHEN change.target_object_id IS NOT NULL THEN 'TARGETED_PLANTING'
        ELSE 'LEGACY_POSITION'
    END,
    placed_at = change.created_at;

ALTER TABLE world_changes
    ADD CONSTRAINT world_changes_ecology_category_check CHECK (
        ecology_category IS NULL OR ecology_category IN ('ANIMAL', 'PLANT', 'MEMORY_OBJECT', 'NON_PLACEABLE')
    ),
    ADD CONSTRAINT world_changes_placement_region_check CHECK (
        placement_region_type IS NULL OR placement_region_type IN ('HUB', 'MEADOW', 'FOREST', 'POND')
    ),
    ADD CONSTRAINT world_changes_placement_reason_check CHECK (
        placement_reason IS NULL OR placement_reason IN (
            'PLACED', 'TARGETED_PLANTING', 'PROFILE_NOT_PLACEABLE',
            'NO_COMPATIBLE_REGION', 'CAPACITY_REACHED', 'NO_SAFE_SPAWN_TILE', 'LEGACY_POSITION'
        )
    );

CREATE INDEX idx_world_changes_ecology_chunk
    ON world_changes (character_id, placement_chunk_x, placement_chunk_y, ecology_category);
CREATE INDEX idx_world_changes_ecology_profile
    ON world_changes (ecology_profile_key, placement_reason);
