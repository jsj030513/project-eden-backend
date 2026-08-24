package com.projecteden.world.npc;

import com.projecteden.character.domain.Character;
import com.projecteden.world.ecology.WorldPlacedObject;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "npc_affinity_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_npc_affinity_event",
                columnNames = {"character_id", "npc_object_id", "event_key"}))
public class NpcAffinityEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "npc_object_id", nullable = false)
    private WorldPlacedObject npcObject;
    @Column(name = "event_key", nullable = false)
    private String eventKey;
    @Column(name = "dialogue_key")
    private String dialogueKey;
    @Column(name = "choice_id")
    private String choiceId;
    @Column(name = "granted_affinity", nullable = false)
    private int grantedAffinity;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected NpcAffinityEvent() { }

    public static NpcAffinityEvent create(
            Character character,
            WorldPlacedObject npcObject,
            String eventKey,
            String dialogueKey,
            String choiceId,
            int grantedAffinity,
            LocalDateTime now) {
        NpcAffinityEvent event = new NpcAffinityEvent();
        event.character = character;
        event.npcObject = npcObject;
        event.eventKey = eventKey;
        event.dialogueKey = dialogueKey;
        event.choiceId = choiceId;
        event.grantedAffinity = grantedAffinity;
        event.createdAt = now;
        return event;
    }
}
