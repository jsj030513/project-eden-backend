ALTER TABLE npc_quest_events
    ADD COLUMN world_id BIGINT,
    ADD COLUMN processing_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN eligible_quest_ids VARCHAR(1024) NOT NULL DEFAULT '',
    ADD COLUMN processed_at TIMESTAMP,
    ADD COLUMN outcome_reason VARCHAR(128),
    ADD COLUMN processing_attempts INTEGER NOT NULL DEFAULT 0;

UPDATE npc_quest_events event
SET world_id = world.id
FROM worlds world
WHERE world.character_id = event.character_id;

ALTER TABLE npc_quest_events
    ALTER COLUMN world_id SET NOT NULL,
    ADD CONSTRAINT fk_npc_quest_event_world
        FOREIGN KEY (world_id) REFERENCES worlds(id) ON DELETE CASCADE,
    ADD CONSTRAINT npc_quest_event_processing_status_check CHECK (
        processing_status IN ('PENDING', 'PROCESSED', 'IGNORED', 'FAILED')
    ),
    ADD CONSTRAINT npc_quest_event_processing_attempts_check CHECK (processing_attempts >= 0);

-- V18 events were synchronously applied (or deliberately ignored) before a processing
-- marker existed. They must never be replayed after upgrading an existing database.
UPDATE npc_quest_events
SET processing_status = 'PROCESSED',
    processed_at = created_at,
    outcome_reason = 'MIGRATED_TERMINAL_EVENT'
WHERE processing_status = 'PENDING';

CREATE INDEX idx_npc_quest_event_replay
    ON npc_quest_events (character_id, world_id, processing_status, created_at, id);
