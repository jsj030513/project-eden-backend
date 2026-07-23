package com.projecteden.world.ecology;

import com.projecteden.ai.dto.RecognitionResponse;

public record PlantMemoryResponse(
        Long photoId,
        Long targetId,
        int targetX,
        int targetY,
        boolean plantingApplied,
        WorldAssetType cropAssetType,
        RecognitionResponse recognition,
        WorldChangeResult worldChange) {
}
