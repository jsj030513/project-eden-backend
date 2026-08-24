package com.projecteden.world.npc;

import java.time.LocalDateTime;
import java.util.List;

public record DialogueSessionResponse(
        String sessionId,
        NpcProjection npc,
        DialogueNodeResponse node,
        boolean completed,
        long conversationCount,
        LocalDateTime expiresAt,
        NpcRelationshipResponse relationship,
        List<NpcProgressNotification> notifications) { }
