ALTER TABLE recognitions
    ADD COLUMN planting_target_object_id BIGINT;

ALTER TABLE recognitions
    ADD CONSTRAINT fk_recognitions_planting_target_object
        FOREIGN KEY (planting_target_object_id)
        REFERENCES world_placed_objects (id)
        ON DELETE RESTRICT;

CREATE INDEX idx_recognitions_planting_target_object_id
    ON recognitions (planting_target_object_id);

ALTER TABLE world_changes
    ADD COLUMN target_object_id BIGINT;

ALTER TABLE world_changes
    ADD CONSTRAINT fk_world_changes_target_object
        FOREIGN KEY (target_object_id)
        REFERENCES world_placed_objects (id)
        ON DELETE RESTRICT;

ALTER TABLE world_changes
    ADD CONSTRAINT uk_world_changes_target_object
        UNIQUE (target_object_id);

ALTER TABLE world_changes
    ADD CONSTRAINT world_changes_target_crop_check CHECK (
        target_object_id IS NULL OR asset_type IN (
            'FARM_CARROT', 'FARM_FLOWER', 'FARM_VEGETABLE', 'FARM_TOMATO', 'FARM_CABBAGE'
        )
    );
