package com.projecteden.world.npc;

/**
 * Named, canonical HUB access tiles used by NPC identities and schedules.
 *
 * <p>The registry keeps schedule resources free of duplicated coordinates and
 * gives validation one authoritative place to resolve an NPC destination.</p>
 */
public enum NpcWorldAnchor {
    MAYOR_HOME(10, 6),
    PLAZA_NORTH(11, 6),
    PLAZA_EAST(12, 7),
    GARDENER_HOME(5, 8),
    FLOWER_GARDEN(6, 6),
    PLAZA_WEST(9, 7),
    ARCHIVE(12, 9),
    COMMUNITY_HOUSE_FRONT(12, 7),
    CARETAKER_HOME(16, 8),
    ANIMAL_AREA(16, 9),
    ANIMAL_AREA_WEST(15, 8);

    private final int tileX;
    private final int tileY;

    NpcWorldAnchor(int tileX, int tileY) {
        this.tileX = tileX;
        this.tileY = tileY;
    }

    public int tileX() { return tileX; }
    public int tileY() { return tileY; }
}
