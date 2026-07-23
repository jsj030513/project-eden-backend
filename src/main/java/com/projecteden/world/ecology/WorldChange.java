package com.projecteden.world.ecology;

import java.time.LocalDateTime;
import com.projecteden.ai.domain.Recognition;
import com.projecteden.character.domain.Character;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "world_changes", uniqueConstraints = {
        @UniqueConstraint(columnNames = "recognition_id"),
        @UniqueConstraint(name = "uk_world_changes_target_object", columnNames = "target_object_id")
})
public class WorldChange {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "character_id", nullable = false) private Character character;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "recognition_id", unique = true) @OnDelete(action = OnDeleteAction.CASCADE) private Recognition recognition;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "target_object_id", unique = true) private WorldPlacedObject targetObject;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private WorldCategory worldCategory;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private WorldAssetType assetType;
    @Column(nullable = false, length = 80) private String messageKey;
    @Column(nullable = false, length = 300) private String displayMessage;
    @Column(nullable = false) private int focusX;
    @Column(nullable = false) private int focusY;
    @Column(nullable = false) private LocalDateTime createdAt;
    protected WorldChange() { }
    private WorldChange(Character character, Recognition recognition, WorldPlacedObject targetObject, WorldCategory category, WorldAssetType assetType, String messageKey, String message, int focusX, int focusY) {
        this.character = character; this.recognition = recognition; this.worldCategory = category; this.assetType = assetType;
        this.targetObject = targetObject;
        this.messageKey = messageKey; this.displayMessage = message; this.focusX = focusX; this.focusY = focusY;
    }
    public static WorldChange create(Character c, Recognition r, WorldCategory category, WorldAssetType assetType, String key, String message, int x, int y) { return new WorldChange(c, r, null, category, assetType, key, message, x, y); }
    public static WorldChange targeted(Character c, Recognition r, WorldPlacedObject target, WorldCategory category, WorldAssetType assetType, String key, String message, int x, int y) { return new WorldChange(c, r, target, category, assetType, key, message, x, y); }
    public static WorldChange template(Character c, WorldCategory category, WorldAssetType assetType, String key, String message, int x, int y) { return new WorldChange(c, null, null, category, assetType, key, message, x, y); }
    @PrePersist void created() { createdAt = LocalDateTime.now(); }
    public Long getId() { return id; } public Character getCharacter() { return character; } public Recognition getRecognition() { return recognition; } public WorldPlacedObject getTargetObject() { return targetObject; } public WorldCategory getWorldCategory() { return worldCategory; } public WorldAssetType getAssetType() { return assetType; } public String getMessageKey() { return messageKey; } public String getDisplayMessage() { return displayMessage; } public int getFocusX() { return focusX; } public int getFocusY() { return focusY; }
}
