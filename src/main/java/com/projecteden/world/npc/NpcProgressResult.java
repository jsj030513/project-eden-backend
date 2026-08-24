package com.projecteden.world.npc;

import java.util.List;

public record NpcProgressResult(
        NpcRelationshipResponse relationship,
        List<NpcProgressNotification> notifications) { }
