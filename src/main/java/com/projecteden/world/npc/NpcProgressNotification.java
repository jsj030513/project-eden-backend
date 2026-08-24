package com.projecteden.world.npc;

public record NpcProgressNotification(
        String type,
        String message,
        int amount) { }
