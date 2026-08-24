package com.projecteden.world.ecology;

import com.projecteden.world.npc.NpcActivity;
import com.projecteden.world.npc.NpcProjection;

public record NpcPositionResponse(
        Long id,
        Long objectId,
        WorldAssetType assetType,
        String npcKey,
        String displayNameKey,
        String displayName,
        String spriteKey,
        String portraitKey,
        int x,
        int y,
        int pixelX,
        int pixelY,
        NpcActivity activity,
        String scheduleSlot,
        boolean canTalk,
        String dialogueKey,
        long stateVersion,
        TileInteractionType interactionType) {

    public NpcPositionResponse(
            Long id,
            WorldAssetType assetType,
            String displayName,
            int x,
            int y,
            TileInteractionType interactionType) {
        this(id, id, assetType, null, null, displayName, null, null,
                x, y, WorldCoordinates.tileToPixel(x), WorldCoordinates.tileToPixel(y),
                NpcActivity.IDLE, "legacy", true, null, 0, interactionType);
    }

    public static NpcPositionResponse from(NpcProjection projection, WorldAssetType assetType) {
        return new NpcPositionResponse(
                projection.objectId(),
                projection.objectId(),
                assetType,
                projection.npcKey(),
                projection.displayNameKey(),
                projection.displayName(),
                projection.spriteKey(),
                projection.portraitKey(),
                projection.tileX(),
                projection.tileY(),
                projection.pixelX(),
                projection.pixelY(),
                projection.activity(),
                projection.scheduleSlot(),
                projection.canTalk(),
                projection.dialogueKey(),
                projection.stateVersion(),
                TileInteractionType.TALK);
    }
}
