package com.projecteden.world.ecology;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "world_placed_objects")
public class WorldPlacedObject {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "world_change_id", nullable = false) @OnDelete(action = OnDeleteAction.CASCADE) private WorldChange worldChange;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private WorldAssetType assetType;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TerrainType terrain;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private HabitatType habitat;
    @Column(nullable = false) private int positionX;
    @Column(nullable = false) private int positionY;
    protected WorldPlacedObject() { }
    private WorldPlacedObject(WorldChange change, WorldAssetType type, TerrainType terrain, HabitatType habitat, int x, int y) { this.worldChange=change; this.assetType=type; this.terrain=terrain; this.habitat=habitat; this.positionX=x; this.positionY=y; }
    public static WorldPlacedObject create(WorldChange c, WorldAssetType t, TerrainType terrain, HabitatType habitat, int x, int y) { return new WorldPlacedObject(c,t,terrain,habitat,x,y); }
    public Long getId() { return id; } public WorldChange getWorldChange() { return worldChange; } public WorldAssetType getAssetType() { return assetType; } public TerrainType getTerrain() { return terrain; } public HabitatType getHabitat() { return habitat; } public int getPositionX() { return positionX; } public int getPositionY() { return positionY; }
}
