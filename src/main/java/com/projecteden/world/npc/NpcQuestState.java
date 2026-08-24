package com.projecteden.world.npc;

import com.projecteden.character.domain.Character;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "npc_quest_states",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_npc_quest_character_quest",
                columnNames = {"character_id", "quest_id"}))
public class NpcQuestState {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;
    @Column(name = "quest_id", nullable = false)
    private String questId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NpcQuestStatus status;
    @Column(nullable = false)
    private int progress;
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    @Column(name = "reward_claimed", nullable = false)
    private boolean rewardClaimed;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected NpcQuestState() { }

    public static NpcQuestState create(
            Character character,
            String questId,
            NpcQuestStatus status,
            LocalDateTime now) {
        NpcQuestState state = new NpcQuestState();
        state.character = character;
        state.questId = questId;
        state.status = status;
        state.updatedAt = now;
        return state;
    }

    public void unlock(LocalDateTime now) {
        if (status == NpcQuestStatus.LOCKED) {
            status = NpcQuestStatus.AVAILABLE;
            updatedAt = now;
        }
    }

    public void activate(LocalDateTime now) {
        if (status == NpcQuestStatus.AVAILABLE) {
            status = NpcQuestStatus.ACTIVE;
            startedAt = now;
            updatedAt = now;
        }
    }

    public boolean progress(int target, LocalDateTime now) {
        if (status == NpcQuestStatus.LOCKED || status == NpcQuestStatus.COMPLETED) return false;
        if (status == NpcQuestStatus.AVAILABLE) {
            status = NpcQuestStatus.ACTIVE;
            startedAt = now;
        }
        progress = Math.min(target, progress + 1);
        updatedAt = now;
        if (progress >= target) {
            status = NpcQuestStatus.COMPLETED;
            completedAt = now;
            return true;
        }
        return false;
    }

    public void resetRepeatable(LocalDateTime now) {
        status = NpcQuestStatus.AVAILABLE;
        progress = 0;
        startedAt = null;
        completedAt = null;
        rewardClaimed = false;
        updatedAt = now;
    }

    public void markRewardClaimed(LocalDateTime now) {
        rewardClaimed = true;
        updatedAt = now;
    }

    public Long getId() { return id; }
    public Character getCharacter() { return character; }
    public String getQuestId() { return questId; }
    public NpcQuestStatus getStatus() { return status; }
    public int getProgress() { return progress; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public boolean isRewardClaimed() { return rewardClaimed; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
