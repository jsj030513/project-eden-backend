package com.projecteden.world.npc;

import com.projecteden.character.domain.Character;
import com.projecteden.world.ecology.WorldPlacedObject;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "npc_affinity_states",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_npc_affinity_character_object",
                columnNames = {"character_id", "npc_object_id"}))
public class NpcAffinityState {
    public static final int MAX_AFFINITY = 1000;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "npc_object_id", nullable = false)
    private WorldPlacedObject npcObject;
    @Column(name = "current_affinity", nullable = false)
    private int currentAffinity;
    @Column(name = "max_affinity", nullable = false)
    private int maxAffinity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AffinityLevel level;
    @Column(name = "last_interaction_at")
    private LocalDateTime lastInteractionAt;
    @Column(name = "conversation_count", nullable = false)
    private long conversationCount;
    @Column(name = "quest_completed_count", nullable = false)
    private long questCompletedCount;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected NpcAffinityState() { }

    public static NpcAffinityState create(
            Character character,
            WorldPlacedObject npcObject,
            LocalDateTime now) {
        NpcAffinityState state = new NpcAffinityState();
        state.character = character;
        state.npcObject = npcObject;
        state.maxAffinity = MAX_AFFINITY;
        state.level = AffinityLevel.STRANGER;
        state.createdAt = now;
        state.updatedAt = now;
        return state;
    }

    public AffinityChange completeConversation(int amount, LocalDateTime now) {
        int before = currentAffinity;
        AffinityLevel previousLevel = level;
        currentAffinity = Math.min(MAX_AFFINITY, currentAffinity + Math.max(0, amount));
        level = AffinityLevel.from(currentAffinity);
        conversationCount++;
        lastInteractionAt = now;
        updatedAt = now;
        return new AffinityChange(currentAffinity - before, previousLevel, level);
    }

    public AffinityChange completeQuest(int amount, LocalDateTime now) {
        int before = currentAffinity;
        AffinityLevel previousLevel = level;
        currentAffinity = Math.min(MAX_AFFINITY, currentAffinity + Math.max(0, amount));
        level = AffinityLevel.from(currentAffinity);
        questCompletedCount++;
        lastInteractionAt = now;
        updatedAt = now;
        return new AffinityChange(currentAffinity - before, previousLevel, level);
    }

    public Long getId() { return id; }
    public Character getCharacter() { return character; }
    public WorldPlacedObject getNpcObject() { return npcObject; }
    public int getCurrentAffinity() { return currentAffinity; }
    public int getMaxAffinity() { return maxAffinity; }
    public AffinityLevel getLevel() { return level; }
    public LocalDateTime getLastInteractionAt() { return lastInteractionAt; }
    public long getConversationCount() { return conversationCount; }
    public long getQuestCompletedCount() { return questCompletedCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public record AffinityChange(int amount, AffinityLevel previousLevel, AffinityLevel level) { }
}
