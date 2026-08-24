-- Align persisted hub landmarks with their authoritative visual and interaction footprints.
UPDATE world_terrain_tiles
SET terrain_type = 'BRIDGE', walkable = TRUE
WHERE tile_y = 13 AND tile_x BETWEEN 17 AND 22;

UPDATE world_terrain_tiles
SET terrain_type = CASE WHEN tile_y = 13 THEN 'ROAD' ELSE 'GRASS' END,
    walkable = TRUE
WHERE tile_x = 23 AND tile_y BETWEEN 11 AND 15;

UPDATE world_terrain_tiles
SET terrain_type = 'ROAD', walkable = TRUE
WHERE tile_x = 14 AND tile_y = 6;

UPDATE world_changes
SET focusx = 672, focusy = 288
WHERE message_key = 'TEMPLATE_HOUSE'
  AND asset_type = 'COMMUNITY_HOUSE'
  AND recognition_id IS NULL;

UPDATE world_placed_objects object
SET positionx = 672, positiony = 288, terrain = 'ROAD'
FROM world_changes change
WHERE object.world_change_id = change.id
  AND change.message_key = 'TEMPLATE_HOUSE'
  AND object.asset_type = 'COMMUNITY_HOUSE'
  AND change.recognition_id IS NULL;

UPDATE worlds
SET village_template_version = 3
WHERE village_template_version >= 2;
