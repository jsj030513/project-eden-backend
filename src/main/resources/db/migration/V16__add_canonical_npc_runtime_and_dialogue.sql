CREATE TABLE npc_runtime_states (
    id BIGSERIAL PRIMARY KEY,
    world_id BIGINT NOT NULL REFERENCES worlds(id) ON DELETE CASCADE,
    npc_object_id BIGINT NOT NULL REFERENCES world_placed_objects(id) ON DELETE CASCADE,
    npc_key VARCHAR(64) NOT NULL,
    tile_x INTEGER NOT NULL,
    tile_y INTEGER NOT NULL,
    activity VARCHAR(32) NOT NULL,
    schedule_slot VARCHAR(64) NOT NULL,
    schedule_date_key VARCHAR(32) NOT NULL,
    last_checkpoint_at TIMESTAMP,
    state_version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_npc_runtime_object UNIQUE (npc_object_id),
    CONSTRAINT npc_runtime_activity_check CHECK (
        activity IN ('IDLE', 'WALKING', 'WORKING', 'RESTING', 'SOCIALIZING')
    )
);

CREATE INDEX idx_npc_runtime_world_coordinate
    ON npc_runtime_states (world_id, tile_x, tile_y);

CREATE TABLE npc_conversation_states (
    id BIGSERIAL PRIMARY KEY,
    world_id BIGINT NOT NULL REFERENCES worlds(id) ON DELETE CASCADE,
    npc_object_id BIGINT NOT NULL REFERENCES world_placed_objects(id) ON DELETE CASCADE,
    character_id BIGINT NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    first_talked_at TIMESTAMP,
    last_talked_at TIMESTAMP,
    conversation_count BIGINT NOT NULL DEFAULT 0,
    last_completed_dialogue_key VARCHAR(96),
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_npc_conversation_character_object UNIQUE (character_id, npc_object_id)
);

CREATE INDEX idx_npc_conversation_world
    ON npc_conversation_states (world_id, character_id);

CREATE TABLE npc_dialogue_sessions (
    id VARCHAR(64) PRIMARY KEY,
    world_id BIGINT NOT NULL REFERENCES worlds(id) ON DELETE CASCADE,
    npc_object_id BIGINT NOT NULL REFERENCES world_placed_objects(id) ON DELETE CASCADE,
    character_id BIGINT NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    dialogue_key VARCHAR(96) NOT NULL,
    current_node_id VARCHAR(96) NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completion_recorded BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_npc_dialogue_session_active
    ON npc_dialogue_sessions (character_id, npc_object_id, completed, expires_at);
