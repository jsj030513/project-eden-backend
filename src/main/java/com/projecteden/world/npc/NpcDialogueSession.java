package com.projecteden.world.npc;

import com.projecteden.character.domain.Character;
import com.projecteden.world.domain.World;
import com.projecteden.world.ecology.WorldPlacedObject;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "npc_dialogue_sessions")
public class NpcDialogueSession {
    @Id
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "world_id", nullable = false)
    private World world;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "npc_object_id", nullable = false)
    private WorldPlacedObject npcObject;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;
    @Column(name = "dialogue_key", nullable = false)
    private String dialogueKey;
    @Column(name = "current_node_id", nullable = false)
    private String currentNodeId;
    @Column(nullable = false)
    private boolean completed;
    @Column(name = "completion_recorded", nullable = false)
    private boolean completionRecorded;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected NpcDialogueSession() { }

    public static NpcDialogueSession create(
            World world,
            WorldPlacedObject object,
            Character character,
            String dialogueKey,
            String startNode,
            LocalDateTime now,
            LocalDateTime expiresAt) {
        NpcDialogueSession session = new NpcDialogueSession();
        session.id = UUID.randomUUID().toString();
        session.world = world;
        session.npcObject = object;
        session.character = character;
        session.dialogueKey = dialogueKey;
        session.currentNodeId = startNode;
        session.createdAt = now;
        session.expiresAt = expiresAt;
        session.updatedAt = now;
        return session;
    }

    public void advance(String nodeId, boolean completed, LocalDateTime now) {
        currentNodeId = nodeId;
        this.completed = completed;
        updatedAt = now;
    }

    public void recordCompletion(LocalDateTime now) {
        completionRecorded = true;
        updatedAt = now;
    }

    public void close(LocalDateTime now) {
        completed = true;
        updatedAt = now;
    }

    public String getId() { return id; }
    public World getWorld() { return world; }
    public WorldPlacedObject getNpcObject() { return npcObject; }
    public Character getCharacter() { return character; }
    public String getDialogueKey() { return dialogueKey; }
    public String getCurrentNodeId() { return currentNodeId; }
    public boolean isCompleted() { return completed; }
    public boolean isCompletionRecorded() { return completionRecorded; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
