package com.projecteden.world.npc;

import com.projecteden.character.domain.Character;
import com.projecteden.world.domain.World;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
        name = "npc_quest_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_npc_quest_event",
                columnNames = {"character_id", "event_key"}))
public class NpcQuestEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "world_id", nullable = false)
    private World world;
    @Column(name = "event_key", nullable = false)
    private String eventKey;
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private NpcQuestEventType eventType;
    private String target;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false)
    private NpcQuestEventProcessingStatus processingStatus;
    @Column(name = "eligible_quest_ids", nullable = false, length = 1024)
    private String eligibleQuestIds;
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    @Column(name = "outcome_reason")
    private String outcomeReason;
    @Column(name = "processing_attempts", nullable = false)
    private int processingAttempts;

    protected NpcQuestEvent() { }

    public static NpcQuestEvent create(
            Character character,
            World world,
            String eventKey,
            NpcQuestEventType eventType,
            String target,
            Set<String> eligibleQuestIds,
            LocalDateTime now) {
        NpcQuestEvent event = new NpcQuestEvent();
        event.character = character;
        event.world = world;
        event.eventKey = eventKey;
        event.eventType = eventType;
        event.target = target;
        event.processingStatus = NpcQuestEventProcessingStatus.PENDING;
        event.eligibleQuestIds = String.join(",", eligibleQuestIds);
        event.createdAt = now;
        return event;
    }

    public void processed(LocalDateTime now, String reason) {
        processingAttempts++;
        processingStatus = NpcQuestEventProcessingStatus.PROCESSED;
        processedAt = now;
        outcomeReason = reason;
    }

    public void ignored(LocalDateTime now, String reason) {
        processingAttempts++;
        processingStatus = NpcQuestEventProcessingStatus.IGNORED;
        processedAt = now;
        outcomeReason = reason;
    }

    public void failed(LocalDateTime now, String reason) {
        processingAttempts++;
        processingStatus = NpcQuestEventProcessingStatus.FAILED;
        processedAt = now;
        outcomeReason = reason;
    }

    public void pending(String reason) {
        outcomeReason = reason;
    }

    public Set<String> eligibleQuestIds() {
        if (eligibleQuestIds == null || eligibleQuestIds.isBlank()) return Set.of();
        return Arrays.stream(eligibleQuestIds.split(","))
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public Long getId() { return id; }
    public Character getCharacter() { return character; }
    public World getWorld() { return world; }
    public String getEventKey() { return eventKey; }
    public NpcQuestEventType getEventType() { return eventType; }
    public String getTarget() { return target; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public NpcQuestEventProcessingStatus getProcessingStatus() { return processingStatus; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public String getOutcomeReason() { return outcomeReason; }
    public int getProcessingAttempts() { return processingAttempts; }
}
