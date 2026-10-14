package com.projecteden.world.ecology;

/**
 * Authoritative tile contract for the fixed hub landmarks.
 *
 * <p>Keeping these coordinates together prevents the persisted terrain,
 * interaction anchors, and frontend artwork from drifting apart.</p>
 */
public final class WorldHubLayout {
    public static final int BRIDGE_MIN_X = 17;
    public static final int BRIDGE_MAX_X = 22;
    public static final int BRIDGE_Y = 13;
    public static final int BRIDGE_ENTRY_X = 16;
    public static final int BRIDGE_EXIT_X = 23;

    public static final int COMMUNITY_HOUSE_MIN_X = 13;
    public static final int COMMUNITY_HOUSE_MAX_X = 15;
    public static final int COMMUNITY_HOUSE_MIN_Y = 3;
    public static final int COMMUNITY_HOUSE_MAX_Y = 5;
    public static final int COMMUNITY_HOUSE_ANCHOR_X = 14;
    public static final int COMMUNITY_HOUSE_ANCHOR_Y = 6;
    public static final int COMMUNITY_HOUSE_APPROACH_X = 14;
    public static final int COMMUNITY_HOUSE_APPROACH_Y = 7;

    private WorldHubLayout() {
    }

    public static boolean isBridge(int x, int y) {
        return y == BRIDGE_Y && x >= BRIDGE_MIN_X && x <= BRIDGE_MAX_X;
    }

    public static boolean isCommunityHouseFootprint(int x, int y) {
        return x >= COMMUNITY_HOUSE_MIN_X && x <= COMMUNITY_HOUSE_MAX_X
                && y >= COMMUNITY_HOUSE_MIN_Y && y <= COMMUNITY_HOUSE_MAX_Y;
    }

    public static boolean isNpcProtectedTile(int x, int y) {
        return isCommunityHouseFootprint(x, y)
                || Math.abs(x - COMMUNITY_HOUSE_APPROACH_X)
                    + Math.abs(y - COMMUNITY_HOUSE_APPROACH_Y) <= 1
                || isBridge(x, y)
                || (x == BRIDGE_ENTRY_X && y == BRIDGE_Y)
                || (x == BRIDGE_EXIT_X && y == BRIDGE_Y)
                || (x == 3 && y == 9);
    }
}
