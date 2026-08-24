package com.projecteden.world.controller;

import com.projecteden.user.domain.User;
import com.projecteden.world.npc.DialogueSessionResponse;
import com.projecteden.world.npc.NpcRelationshipResponse;
import com.projecteden.world.npc.NpcRelationshipService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import com.projecteden.world.npc.WorldNpcDialogueService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/worlds/me/npcs")
public class NpcWorldController {
    private final WorldNpcDialogueService dialogues;
    private final NpcRelationshipService relationships;

    public NpcWorldController(
            WorldNpcDialogueService dialogues,
            NpcRelationshipService relationships) {
        this.dialogues = dialogues;
        this.relationships = relationships;
    }

    @GetMapping("/relationships")
    public ResponseEntity<List<NpcRelationshipResponse>> relationships(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(relationships.relationships(user.getId()));
    }

    @GetMapping("/{objectId}/relationship")
    public ResponseEntity<NpcRelationshipResponse> relationship(
            @AuthenticationPrincipal User user,
            @PathVariable Long objectId) {
        return ResponseEntity.ok(relationships.relationship(user.getId(), objectId));
    }

    @PostMapping("/{objectId}/dialogues/start")
    public ResponseEntity<DialogueSessionResponse> start(
            @AuthenticationPrincipal User user,
            @PathVariable Long objectId) {
        return ResponseEntity.ok(dialogues.start(user.getId(), objectId));
    }

    @PostMapping("/{objectId}/dialogues/{sessionId}/choices/{choiceId}")
    public ResponseEntity<DialogueSessionResponse> choose(
            @AuthenticationPrincipal User user,
            @PathVariable Long objectId,
            @PathVariable String sessionId,
            @PathVariable String choiceId) {
        return ResponseEntity.ok(dialogues.choose(user.getId(), objectId, sessionId, choiceId));
    }

    @PostMapping("/{objectId}/dialogues/{sessionId}/close")
    public ResponseEntity<Void> close(
            @AuthenticationPrincipal User user,
            @PathVariable Long objectId,
            @PathVariable String sessionId) {
        dialogues.close(user.getId(), objectId, sessionId);
        return ResponseEntity.noContent().build();
    }
}
