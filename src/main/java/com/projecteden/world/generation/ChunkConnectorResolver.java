package com.projecteden.world.generation;

import com.projecteden.world.domain.World;
import org.springframework.stereotype.Component;

@Component
public class ChunkConnectorResolver {

    public ResolvedConnector resolve(
            World world,
            int chunkAX,
            int chunkAY,
            int chunkBX,
            int chunkBY) {
        int distance = Math.abs(chunkAX - chunkBX) + Math.abs(chunkAY - chunkBY);
        if (distance != 1) throw new IllegalArgumentException("CHUNKS_DO_NOT_SHARE_AN_EDGE");
        String axis = chunkAX == chunkBX ? "HORIZONTAL" : "VERTICAL";
        int lowX = Math.min(chunkAX, chunkBX);
        int lowY = Math.min(chunkAY, chunkBY);
        String edgeKey = world.getSeed() + ":" + lowX + ":" + lowY + ":" + axis
                + ":" + world.getWorldGenerationVersion();
        int offset = chunkAX == chunkBX ? 3 : 7;
        return new ResolvedConnector(edgeKey, ChunkConnectorType.ROAD, offset);
    }

    public record ResolvedConnector(String edgeKey, ChunkConnectorType type, int offset) {
    }
}
