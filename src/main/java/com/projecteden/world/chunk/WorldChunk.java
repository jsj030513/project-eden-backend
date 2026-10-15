package com.projecteden.world.chunk;

import com.projecteden.world.domain.World;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(name = "world_chunks", uniqueConstraints = @UniqueConstraint(
        name = "uk_world_chunks_world_coordinate",
        columnNames = {"world_id", "chunk_x", "chunk_y"}))
public class WorldChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "world_id", nullable = false)
    private World world;

    @Column(name = "chunk_x", nullable = false)
    private int chunkX;

    @Column(name = "chunk_y", nullable = false)
    private int chunkY;

    @Enumerated(EnumType.STRING)
    @Column(name = "region_type", nullable = false)
    private WorldChunkRegionType regionType;

    @Column(name = "template_key", nullable = false, length = 64)
    private String templateKey;

    @Column(name = "generation_version", nullable = false)
    private int generationVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorldChunkStatus status;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "discovered_at")
    private LocalDateTime discoveredAt;

    protected WorldChunk() {
    }

    private WorldChunk(
            World world,
            int chunkX,
            int chunkY,
            WorldChunkRegionType regionType,
            String templateKey,
            int generationVersion,
            boolean discovered) {
        this.world = world;
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.regionType = regionType;
        this.templateKey = templateKey;
        this.generationVersion = generationVersion;
        this.status = WorldChunkStatus.GENERATED;
        if (discovered) this.discoveredAt = LocalDateTime.now();
    }

    public static WorldChunk hub(World world, int chunkX, int chunkY) {
        return new WorldChunk(
                world, chunkX, chunkY, WorldChunkRegionType.HUB,
                "HUB_" + chunkX + "_" + chunkY,
                world.getWorldGenerationVersion(), true);
    }

    public static WorldChunk generated(
            World world,
            int chunkX,
            int chunkY,
            WorldChunkRegionType regionType,
            String templateKey,
            int generationVersion) {
        if (regionType == WorldChunkRegionType.HUB) {
            throw new IllegalArgumentException("OUTER_CHUNK_CANNOT_USE_HUB_TEMPLATE");
        }
        return new WorldChunk(
                world, chunkX, chunkY, regionType, templateKey,
                generationVersion, false);
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (generatedAt == null) generatedAt = now;
        if (regionType == WorldChunkRegionType.HUB && discoveredAt == null) discoveredAt = now;
    }

    public boolean discover(LocalDateTime discoveredAt) {
        if (this.discoveredAt != null) return false;
        this.discoveredAt = discoveredAt;
        return true;
    }

    public void repair(
            WorldChunkRegionType regionType,
            String templateKey,
            int generationVersion) {
        this.regionType = regionType;
        this.templateKey = templateKey;
        this.generationVersion = generationVersion;
        this.status = WorldChunkStatus.GENERATED;
        this.generatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public World getWorld() { return world; }
    public int getChunkX() { return chunkX; }
    public int getChunkY() { return chunkY; }
    public WorldChunkRegionType getRegionType() { return regionType; }
    public String getTemplateKey() { return templateKey; }
    public int getGenerationVersion() { return generationVersion; }
    public WorldChunkStatus getStatus() { return status; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public LocalDateTime getDiscoveredAt() { return discoveredAt; }
}
