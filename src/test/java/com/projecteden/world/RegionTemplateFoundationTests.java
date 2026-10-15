package com.projecteden.world;

import static org.assertj.core.api.Assertions.assertThat;

import com.projecteden.world.chunk.WorldChunkRegionType;
import com.projecteden.world.generation.RegionTemplateRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RegionTemplateFoundationTests {
    @Autowired RegionTemplateRegistry regions;

    @Test
    void regionTemplatesExposeValidatedBoundedSpawnZoneContracts() {
        for (WorldChunkRegionType type : java.util.List.of(
                WorldChunkRegionType.MEADOW, WorldChunkRegionType.FOREST, WorldChunkRegionType.POND)) {
            assertThat(regions.require(type).spawnZones()).isNotEmpty().allSatisfy(zone -> {
                assertThat(zone.tag()).isNotBlank();
                assertThat(zone.capacity()).isPositive();
                assertThat(zone.capacity()).isLessThanOrEqualTo(zone.width() * zone.height());
                assertThat(zone.terrainRequirements()).isNotEmpty();
                assertThat(zone.allowedEcologyCategories()).isNotEmpty();
                assertThat(zone.interactionAccessRequired()).isTrue();
            });
        }
    }
}
