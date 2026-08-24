package com.projecteden.world.ecology;
import java.util.List;
public record WorldChangeResult(Long worldChangeId, WorldCategory worldCategory, WorldAssetType assetType, String messageKey, String displayMessage, List<Long> spawnedObjectIds, boolean villageChanged, int focusX, int focusY, EcologyPlacementResult ecologyPlacement) {
    public WorldChangeResult(Long worldChangeId, WorldCategory worldCategory, WorldAssetType assetType, String messageKey, String displayMessage, List<Long> spawnedObjectIds, boolean villageChanged, int focusX, int focusY) {
        this(worldChangeId, worldCategory, assetType, messageKey, displayMessage, spawnedObjectIds, villageChanged, focusX, focusY, null);
    }
}
