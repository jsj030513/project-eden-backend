package com.projecteden.world.npc;

import java.time.LocalDateTime;
import java.util.List;

public record NpcRelationshipResponse(
        Long npcObjectId,
        String npcKey,
        int currentAffinity,
        int maxAffinity,
        AffinityLevel level,
        String relationship,
        LocalDateTime lastInteractionAt,
        long conversationCount,
        long questCompletedCount,
        List<NpcQuestResponse> quests) { }
