package com.projecteden.world.npc;

public record NpcProjection(
        Long objectId,
        String npcKey,
        String displayNameKey,
        String displayName,
        String spriteKey,
        String portraitKey,
        int tileX,
        int tileY,
        int pixelX,
        int pixelY,
        NpcActivity activity,
        String scheduleSlot,
        boolean canTalk,
        String dialogueKey,
        long stateVersion) { }
