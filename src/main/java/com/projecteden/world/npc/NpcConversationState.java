package com.projecteden.world.npc;

import com.projecteden.character.domain.Character;
import com.projecteden.world.domain.World;
import com.projecteden.world.ecology.WorldPlacedObject;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "npc_conversation_states",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_npc_conversation_character_object",
                columnNames = {"character_id", "npc_object_id"}))
public class NpcConversationState {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "world_id", nullable = false)
    private World world;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "npc_object_id", nullable = false)
    private WorldPlacedObject npcObject;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;
    @Column(name = "first_talked_at")
    private LocalDateTime firstTalkedAt;
    @Column(name = "last_talked_at")
    private LocalDateTime lastTalkedAt;
    @Column(name = "conversation_count", nullable = false)
    private long conversationCount;
    @Column(name = "last_completed_dialogue_key")
    private String lastCompletedDialogueKey;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected NpcConversationState() { }

    public static NpcConversationState create(
            World world,
            WorldPlacedObject object,
            Character character,
            LocalDateTime now) {
        NpcConversationState state = new NpcConversationState();
        state.world = world;
        state.npcObject = object;
        state.character = character;
        state.updatedAt = now;
        return state;
    }

    public void complete(String dialogueKey, LocalDateTime now) {
        if (firstTalkedAt == null) firstTalkedAt = now;
        lastTalkedAt = now;
        conversationCount++;
        lastCompletedDialogueKey = dialogueKey;
        updatedAt = now;
    }

    public LocalDateTime getFirstTalkedAt() { return firstTalkedAt; }
    public LocalDateTime getLastTalkedAt() { return lastTalkedAt; }
    public long getConversationCount() { return conversationCount; }
    public String getLastCompletedDialogueKey() { return lastCompletedDialogueKey; }
}
