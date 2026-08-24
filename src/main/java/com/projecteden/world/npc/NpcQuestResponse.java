package com.projecteden.world.npc;

import java.time.LocalDateTime;

public record NpcQuestResponse(
        String questId,
        String npcKey,
        String title,
        String description,
        NpcQuestStatus status,
        int progress,
        int target,
        boolean repeatable,
        boolean hidden,
        LocalDateTime startedAt,
        LocalDateTime completedAt) { }
