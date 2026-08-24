package com.projecteden.world.npc;

public enum AffinityLevel {
    STRANGER(0),
    ACQUAINTANCE(100),
    FRIEND(200),
    CLOSE_FRIEND(400),
    BEST_FRIEND(700);

    private final int minimum;

    AffinityLevel(int minimum) {
        this.minimum = minimum;
    }

    public int minimum() {
        return minimum;
    }

    public static AffinityLevel from(int affinity) {
        AffinityLevel result = STRANGER;
        for (AffinityLevel level : values()) {
            if (affinity >= level.minimum) result = level;
        }
        return result;
    }
}
