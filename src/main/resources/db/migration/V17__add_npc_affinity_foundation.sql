CREATE TABLE npc_affinity_states (
    id BIGSERIAL PRIMARY KEY,
    character_id BIGINT NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    npc_object_id BIGINT NOT NULL REFERENCES world_placed_objects(id) ON DELETE CASCADE,
    current_affinity INTEGER NOT NULL DEFAULT 0,
    max_affinity INTEGER NOT NULL DEFAULT 1000,
    level VARCHAR(32) NOT NULL DEFAULT 'STRANGER',
    last_interaction_at TIMESTAMP,
    conversation_count BIGINT NOT NULL DEFAULT 0,
    quest_completed_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_npc_affinity_character_object UNIQUE (character_id, npc_object_id),
    CONSTRAINT npc_affinity_range_check CHECK (
        current_affinity BETWEEN 0 AND 1000 AND max_affinity = 1000
    ),
    CONSTRAINT npc_affinity_level_check CHECK (
        level IN ('STRANGER', 'ACQUAINTANCE', 'FRIEND', 'CLOSE_FRIEND', 'BEST_FRIEND')
    )
);

CREATE INDEX idx_npc_affinity_character
    ON npc_affinity_states (character_id, npc_object_id);

CREATE TABLE npc_affinity_events (
    id BIGSERIAL PRIMARY KEY,
    character_id BIGINT NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    npc_object_id BIGINT NOT NULL REFERENCES world_placed_objects(id) ON DELETE CASCADE,
    event_key VARCHAR(160) NOT NULL,
    dialogue_key VARCHAR(96),
    choice_id VARCHAR(96),
    granted_affinity INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_npc_affinity_event UNIQUE (character_id, npc_object_id, event_key)
);

CREATE INDEX idx_npc_affinity_event_history
    ON npc_affinity_events (character_id, npc_object_id, created_at);
