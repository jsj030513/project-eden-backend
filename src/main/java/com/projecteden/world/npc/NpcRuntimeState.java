package com.projecteden.world.npc;

import com.projecteden.world.domain.World;
import com.projecteden.world.ecology.WorldPlacedObject;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "npc_runtime_states",
        uniqueConstraints = @UniqueConstraint(name = "uk_npc_runtime_object", columnNames = "npc_object_id"))
public class NpcRuntimeState {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "world_id", nullable = false)
    private World world;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "npc_object_id", nullable = false)
    private WorldPlacedObject npcObject;
    @Enumerated(EnumType.STRING) @Column(name = "npc_key", nullable = false)
    private CanonicalNpcKey npcKey;
    @Column(name = "tile_x", nullable = false)
    private int tileX;
    @Column(name = "tile_y", nullable = false)
    private int tileY;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private NpcActivity activity;
    @Column(name = "schedule_slot", nullable = false)
    private String scheduleSlot;
    @Column(name = "schedule_date_key", nullable = false)
    private String scheduleDateKey;
    @Column(name = "last_checkpoint_at")
    private LocalDateTime lastCheckpointAt;
    @Version @Column(name = "state_version", nullable = false)
    private long stateVersion;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected NpcRuntimeState() { }

    public static NpcRuntimeState create(
            World world,
            WorldPlacedObject object,
            CanonicalNpcKey key,
            int tileX,
            int tileY,
            String dateKey,
            LocalDateTime now) {
        NpcRuntimeState state = new NpcRuntimeState();
        state.world = world;
        state.npcObject = object;
        state.npcKey = key;
        state.tileX = tileX;
        state.tileY = tileY;
        state.activity = NpcActivity.IDLE;
        state.scheduleSlot = "bootstrap";
        state.scheduleDateKey = dateKey;
        state.updatedAt = now;
        return state;
    }

    public void checkpoint(
            int nextX,
            int nextY,
            NpcActivity nextActivity,
            String nextSlot,
            String dateKey,
            LocalDateTime checkpointAt) {
        tileX = nextX;
        tileY = nextY;
        activity = nextActivity;
        scheduleSlot = nextSlot;
        scheduleDateKey = dateKey;
        lastCheckpointAt = checkpointAt;
        updatedAt = checkpointAt;
    }

    public Long getId() { return id; }
    public World getWorld() { return world; }
    public WorldPlacedObject getNpcObject() { return npcObject; }
    public CanonicalNpcKey getNpcKey() { return npcKey; }
    public int getTileX() { return tileX; }
    public int getTileY() { return tileY; }
    public NpcActivity getActivity() { return activity; }
    public String getScheduleSlot() { return scheduleSlot; }
    public String getScheduleDateKey() { return scheduleDateKey; }
    public LocalDateTime getLastCheckpointAt() { return lastCheckpointAt; }
    public long getStateVersion() { return stateVersion; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
