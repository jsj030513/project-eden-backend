CREATE TABLE npc_quest_states (
    id BIGSERIAL PRIMARY KEY,
    character_id BIGINT NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    quest_id VARCHAR(96) NOT NULL,
    status VARCHAR(24) NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    reward_claimed BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_npc_quest_character_quest UNIQUE (character_id, quest_id),
    CONSTRAINT npc_quest_status_check CHECK (
        status IN ('LOCKED', 'AVAILABLE', 'ACTIVE', 'COMPLETED')
    ),
    CONSTRAINT npc_quest_progress_check CHECK (progress >= 0)
);

CREATE INDEX idx_npc_quest_character_status
    ON npc_quest_states (character_id, status, quest_id);

CREATE TABLE npc_quest_events (
    id BIGSERIAL PRIMARY KEY,
    character_id BIGINT NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    event_key VARCHAR(160) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    target VARCHAR(128),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_npc_quest_event UNIQUE (character_id, event_key),
    CONSTRAINT npc_quest_event_type_check CHECK (
        event_type IN (
            'TALK', 'VISIT_LOCATION', 'TAKE_PHOTO', 'INSPECT',
            'ANIMAL_INTERACTION', 'COMMUNITY_VISIT'
        )
    )
);
