package com.projecteden.world.ecology;

import static org.assertj.core.api.Assertions.assertThat;

import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.world.chunk.WorldChunkRegionType;
import com.projecteden.world.generation.RegionTemplateRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PhotoEcologyProfileRegistryTests {
    @Autowired PhotoEcologyProfileRegistry profiles;
    @Autowired RegionTemplateRegistry regions;

    @Test
    void explicitlyCoversEveryRecognitionWithVersionedDeterministicRegistry() {
        assertThat(profiles.profileCount()).isEqualTo(RecognizedObject.values().length);
        assertThat(profiles.checksum()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(java.util.Arrays.stream(RecognizedObject.values()).map(profiles::require))
                .allSatisfy(profile -> {
                    assertThat(profile.version()).isEqualTo(PhotoEcologyProfileRegistry.VERSION);
                    assertThat(profile.allowedRegions()).containsAll(profile.preferredRegions());
                    assertThat(profile.allowedTerrain()).containsAll(profile.preferredTerrain());
                    assertThat(profile.maxPerChunk()).isPositive();
                    assertThat(profile.maxPerZone()).isPositive();
                    assertThat(profile.fallbackPolicy()).isNotNull();
                });
    }

    @Test
    void preservesSpecificAnimalPlantCropAndUnknownPolicies() {
        assertThat(profiles.require(RecognizedObject.DOG).preferredRegions())
                .containsExactly(WorldChunkRegionType.MEADOW);
        assertThat(profiles.require(RecognizedObject.CAT).allowedRegions())
                .contains(WorldChunkRegionType.FOREST);
        assertThat(profiles.require(RecognizedObject.BIRD).allowedTerrain())
                .doesNotContain(TerrainType.WATER);
        assertThat(profiles.require(RecognizedObject.FLOWER).ecologyCategory()).isEqualTo(EcologyCategory.PLANT);
        assertThat(profiles.require(RecognizedObject.CARROT).projectedAssetType())
                .isEqualTo(WorldAssetType.BAKERY_DETAIL);
        assertThat(profiles.require(RecognizedObject.UNKNOWN).ecologyCategory())
                .isEqualTo(EcologyCategory.NON_PLACEABLE);
    }

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
