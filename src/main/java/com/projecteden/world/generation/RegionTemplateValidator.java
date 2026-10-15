package com.projecteden.world.generation;

import com.projecteden.world.ecology.TerrainType;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RegionTemplateValidator {

    private static final Set<Character> TERRAIN_CODES = Set.of('G', 'R', 'E', 'F', 'W', 'B', 'X');
    private static final Set<String> EDGES = Set.of("north", "east", "south", "west");

    public void validate(RegionTemplate template) {
        if (template.templateKey() == null || template.templateKey().isBlank()) {
            throw new IllegalStateException("REGION_TEMPLATE_KEY_REQUIRED");
        }
        if (template.width() != 8 || template.height() != 8) {
            throw new IllegalStateException("REGION_TEMPLATE_MUST_BE_8_BY_8");
        }
        if (template.terrainPattern() == null || template.terrainPattern().size() != template.height()) {
            throw new IllegalStateException("REGION_TEMPLATE_HEIGHT_MISMATCH");
        }
        for (String row : template.terrainPattern()) {
            if (row == null || row.length() != template.width()) {
                throw new IllegalStateException("REGION_TEMPLATE_WIDTH_MISMATCH");
            }
            row.chars().mapToObj(value -> (char) value).forEach(code -> {
                if (!TERRAIN_CODES.contains(code)) {
                    throw new IllegalStateException("UNKNOWN_REGION_TERRAIN_CODE_" + code);
                }
            });
        }
        if (template.connectors() == null || !template.connectors().keySet().equals(EDGES)) {
            throw new IllegalStateException("REGION_TEMPLATE_CONNECTORS_REQUIRED");
        }
        template.connectors().forEach((edge, connector) -> {
            if (connector == null || connector.type() == null
                    || connector.offset() < 0 || connector.offset() >= 8) {
                throw new IllegalStateException("INVALID_REGION_CONNECTOR_" + edge);
            }
            int x = switch (edge) {
                case "west" -> 0;
                case "east" -> 7;
                default -> connector.offset();
            };
            int y = switch (edge) {
                case "north" -> 0;
                case "south" -> 7;
                default -> connector.offset();
            };
            if (!terrain(template.terrainPattern().get(y).charAt(x)).isLandWalkable()) {
                throw new IllegalStateException("BLOCKED_REGION_CONNECTOR_" + edge);
            }
        });
        validateZones(template);
        validateConnectivity(template);
    }

    private static void validateZones(RegionTemplate template) {
        Set<String> coordinates = new HashSet<>();
        for (RegionTemplate.Decoration decoration : template.optionalDecorations()) {
            validateCoordinate(decoration.x(), decoration.y());
            if (!coordinates.add(decoration.x() + ":" + decoration.y())) {
                throw new IllegalStateException("DUPLICATE_REGION_DECORATION_COORDINATE");
            }
        }
        Set<String> zoneCoordinates = new HashSet<>();
        template.spawnZones().forEach(zone -> validateZone(template, zone, zoneCoordinates));
        template.interactionZones().forEach(zone -> validateZone(template, zone, zoneCoordinates));
    }

    private static void validateZone(RegionTemplate template, RegionTemplate.Zone zone, Set<String> coordinates) {
        if (zone.tag() == null || zone.tag().isBlank()) throw new IllegalStateException("REGION_ZONE_TAG_REQUIRED");
        if (zone.width() <= 0 || zone.height() <= 0 || zone.capacity() <= 0 || zone.minSpacing() < 0) {
            throw new IllegalStateException("INVALID_REGION_ZONE_CONTRACT_" + zone.tag());
        }
        validateCoordinate(zone.x(), zone.y());
        validateCoordinate(zone.x() + zone.width() - 1, zone.y() + zone.height() - 1);
        int viable = 0;
        for (int y = zone.y(); y < zone.y() + zone.height(); y++) {
            for (int x = zone.x(); x < zone.x() + zone.width(); x++) {
                String coordinate = x + ":" + y;
                if (!coordinates.add(coordinate)) throw new IllegalStateException("DUPLICATE_REGION_ZONE_COORDINATE_" + coordinate);
                TerrainType terrain = terrain(template.terrainPattern().get(y).charAt(x));
                if (zone.terrainRequirements() != null && zone.terrainRequirements().contains(terrain)
                        && (!zone.walkableRequired() || terrain.isLandWalkable())) viable++;
                if (isConnector(template, x, y)) throw new IllegalStateException("REGION_ZONE_ON_CONNECTOR_" + zone.tag());
            }
        }
        if (zone.allowedEcologyCategories() == null || zone.allowedEcologyCategories().isEmpty()
                || zone.terrainRequirements() == null || zone.terrainRequirements().isEmpty()) {
            throw new IllegalStateException("REGION_ZONE_COMPATIBILITY_REQUIRED_" + zone.tag());
        }
        if (zone.capacity() > viable) throw new IllegalStateException("REGION_ZONE_CAPACITY_EXCEEDS_TILES_" + zone.tag());
        if (zone.interactionAccessRequired() && !hasAccessTile(template, zone)) {
            throw new IllegalStateException("REGION_ZONE_HAS_NO_ACCESS_" + zone.tag());
        }
    }

    private static boolean isConnector(RegionTemplate template, int x, int y) {
        return template.connectors().entrySet().stream().anyMatch(entry -> {
            RegionTemplate.Connector connector = entry.getValue();
            int connectorX = switch (entry.getKey()) { case "west" -> 0; case "east" -> 7; default -> connector.offset(); };
            int connectorY = switch (entry.getKey()) { case "north" -> 0; case "south" -> 7; default -> connector.offset(); };
            return connectorX == x && connectorY == y;
        });
    }

    private static boolean hasAccessTile(RegionTemplate template, RegionTemplate.Zone zone) {
        for (int y = zone.y(); y < zone.y() + zone.height(); y++) {
            for (int x = zone.x(); x < zone.x() + zone.width(); x++) {
                for (int[] delta : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                    int adjacentX = x + delta[0];
                    int adjacentY = y + delta[1];
                    if (adjacentX >= 0 && adjacentX < 8 && adjacentY >= 0 && adjacentY < 8
                            && terrain(template.terrainPattern().get(adjacentY).charAt(adjacentX)).isLandWalkable()) return true;
                }
            }
        }
        return false;
    }

    private static void validateCoordinate(int x, int y) {
        if (x < 0 || x >= 8 || y < 0 || y >= 8) {
            throw new IllegalStateException("REGION_TEMPLATE_COORDINATE_OUT_OF_BOUNDS");
        }
    }

    private static void validateConnectivity(RegionTemplate template) {
        RegionTemplate.Connector north = template.connectors().get("north");
        int startX = north.offset();
        int startY = 0;
        Set<String> visited = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startY});
        visited.add(startX + ":" + startY);
        while (!queue.isEmpty()) {
            int[] current = queue.removeFirst();
            for (int[] delta : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = current[0] + delta[0];
                int y = current[1] + delta[1];
                if (x < 0 || x >= 8 || y < 0 || y >= 8
                        || !terrain(template.terrainPattern().get(y).charAt(x)).isLandWalkable()
                        || !visited.add(x + ":" + y)) continue;
                queue.add(new int[]{x, y});
            }
        }
        template.connectors().forEach((edge, connector) -> {
            int x = switch (edge) {
                case "west" -> 0;
                case "east" -> 7;
                default -> connector.offset();
            };
            int y = switch (edge) {
                case "north" -> 0;
                case "south" -> 7;
                default -> connector.offset();
            };
            if (!visited.contains(x + ":" + y)) {
                throw new IllegalStateException("ISOLATED_REGION_CONNECTOR_" + edge);
            }
        });
    }

    public static TerrainType terrain(char code) {
        return switch (code) {
            case 'G' -> TerrainType.GRASS;
            case 'R' -> TerrainType.ROAD;
            case 'E' -> TerrainType.FLOWER_FIELD;
            case 'F' -> TerrainType.FOREST;
            case 'W' -> TerrainType.WATER;
            case 'B' -> TerrainType.BRIDGE;
            case 'X' -> TerrainType.ROCK;
            default -> throw new IllegalArgumentException("UNKNOWN_REGION_TERRAIN_CODE_" + code);
        };
    }
}
