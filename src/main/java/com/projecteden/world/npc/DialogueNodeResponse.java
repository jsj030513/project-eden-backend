package com.projecteden.world.npc;

import java.util.List;

public record DialogueNodeResponse(
        String id,
        String speaker,
        String text,
        List<DialogueChoiceResponse> choices,
        boolean close) { }
