CREATE TABLE world_chunks (
    id BIGSERIAL PRIMARY KEY,
    world_id BIGINT NOT NULL REFERENCES worlds(id) ON DELETE CASCADE,
    chunk_x INTEGER NOT NULL,
    chunk_y INTEGER NOT NULL,
    region_type VARCHAR(32) NOT NULL,
    template_key VARCHAR(64) NOT NULL,
    generation_version INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    generated_at TIMESTAMP NOT NULL,
    discovered_at TIMESTAMP,
    CONSTRAINT uk_world_chunks_world_coordinate UNIQUE (world_id, chunk_x, chunk_y),
    CONSTRAINT world_chunks_region_type_check CHECK (region_type IN ('HUB')),
    CONSTRAINT world_chunks_status_check CHECK (status IN ('GENERATED'))
);

CREATE INDEX idx_world_chunks_world_coordinate
    ON world_chunks (world_id, chunk_x, chunk_y);

CREATE INDEX idx_world_chunks_world_discovered
    ON world_chunks (world_id, discovered_at);

INSERT INTO world_chunks (
    world_id, chunk_x, chunk_y, region_type, template_key,
    generation_version, status, generated_at, discovered_at
)
SELECT world.id, chunk_x, chunk_y, 'HUB',
       'HUB_' || chunk_x || '_' || chunk_y,
       world.world_generation_version, 'GENERATED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM worlds world
CROSS JOIN (VALUES (0), (1), (2)) AS chunk_x_values(chunk_x)
CROSS JOIN (VALUES (0), (1)) AS chunk_y_values(chunk_y)
ON CONFLICT (world_id, chunk_x, chunk_y) DO NOTHING;
