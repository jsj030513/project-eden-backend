-- Keep PostgreSQL CHECK constraints aligned with the Java world domain.
ALTER TABLE world_changes
    DROP CONSTRAINT IF EXISTS world_changes_asset_type_check;
ALTER TABLE world_changes
    ADD CONSTRAINT world_changes_asset_type_check CHECK (asset_type IN (
        'FLOWER_CLUSTER', 'TREE_GROVE', 'POND', 'VISITOR', 'WATER_VISITOR', 'MIXED_VISITOR',
        'STORYBOOK_RESIDENT', 'BAKERY_DETAIL', 'CAFE_DETAIL', 'LIBRARY_DETAIL', 'ROAD_DETAIL',
        'ATMOSPHERE', 'MEMORY_SPARK', 'PLAZA', 'FARM_PLOT_EMPTY', 'FARM_CARROT', 'FARM_FLOWER',
        'FARM_VEGETABLE', 'FARM_TOMATO', 'FARM_CABBAGE', 'COMMUNITY_HOUSE', 'BUSH',
        'DEFAULT_NPC_GUIDE', 'DEFAULT_NPC_GARDENER', 'DEFAULT_NPC_MEMORY_KEEPER',
        'DEFAULT_NPC_ANIMAL_CARETAKER', 'DEFAULT_DOG', 'DEFAULT_CAT', 'DEFAULT_BIRD'
    ));

ALTER TABLE world_placed_objects
    DROP CONSTRAINT IF EXISTS world_placed_objects_asset_type_check;
ALTER TABLE world_placed_objects
    ADD CONSTRAINT world_placed_objects_asset_type_check CHECK (asset_type IN (
        'FLOWER_CLUSTER', 'TREE_GROVE', 'POND', 'VISITOR', 'WATER_VISITOR', 'MIXED_VISITOR',
        'STORYBOOK_RESIDENT', 'BAKERY_DETAIL', 'CAFE_DETAIL', 'LIBRARY_DETAIL', 'ROAD_DETAIL',
        'ATMOSPHERE', 'MEMORY_SPARK', 'PLAZA', 'FARM_PLOT_EMPTY', 'FARM_CARROT', 'FARM_FLOWER',
        'FARM_VEGETABLE', 'FARM_TOMATO', 'FARM_CABBAGE', 'COMMUNITY_HOUSE', 'BUSH',
        'DEFAULT_NPC_GUIDE', 'DEFAULT_NPC_GARDENER', 'DEFAULT_NPC_MEMORY_KEEPER',
        'DEFAULT_NPC_ANIMAL_CARETAKER', 'DEFAULT_DOG', 'DEFAULT_CAT', 'DEFAULT_BIRD'
    ));
ALTER TABLE world_placed_objects
    DROP CONSTRAINT IF EXISTS world_placed_objects_terrain_check;
ALTER TABLE world_placed_objects
    ADD CONSTRAINT world_placed_objects_terrain_check CHECK (terrain IN (
        'GRASS', 'ROAD', 'SOIL', 'FLOWER_FIELD', 'FOREST', 'WATER', 'BRIDGE', 'BEACH',
        'BUILDING', 'ROCK', 'CLIFF'
    ));

ALTER TABLE world_terrain_tiles
    DROP CONSTRAINT IF EXISTS world_terrain_tiles_terrain_type_check;
ALTER TABLE world_terrain_tiles
    ADD CONSTRAINT world_terrain_tiles_terrain_type_check CHECK (terrain_type IN (
        'GRASS', 'ROAD', 'SOIL', 'FLOWER_FIELD', 'FOREST', 'WATER', 'BRIDGE', 'BEACH',
        'BUILDING', 'ROCK', 'CLIFF'
    ));
