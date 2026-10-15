package com.projecteden.world;

import static org.assertj.core.api.Assertions.assertThat;

import com.projecteden.character.domain.Character;
import com.projecteden.character.domain.CharacterGender;
import com.projecteden.character.domain.CharacterJob;
import com.projecteden.character.domain.HairStyle;
import com.projecteden.character.domain.Outfit;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.world.chunk.WorldChunkManager;
import com.projecteden.world.chunk.WorldChunkRepository;
import com.projecteden.world.chunk.WorldChunkRegionType;
import com.projecteden.world.domain.World;
import com.projecteden.world.ecology.WorldTerrainTileRepository;
import com.projecteden.world.ecology.TerrainType;
import com.projecteden.world.generation.ChunkGenerationService;
import com.projecteden.world.repository.WorldRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WorldChunkFoundationIntegrationTests {
    @Autowired WorldChunkManager manager;
    @Autowired ChunkGenerationService generation;
    @Autowired WorldChunkRepository chunks;
    @Autowired WorldTerrainTileRepository terrain;
    @Autowired UserRepository users;
    @Autowired CharacterRepository characters;
    @Autowired WorldRepository worlds;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void bootstrapsExactlySixHubChunksAndRecoversMissingMetadata() {
        Fixture fixture = fixture("bootstrap");

        manager.ensureHubChunks(fixture.world());
        assertThat(chunks.findByWorldIdOrderByChunkYAscChunkXAsc(fixture.world().getId()))
                .hasSize(6)
                .extracting(chunk -> chunk.getChunkX() + ":" + chunk.getChunkY())
                .containsExactly("0:0", "1:0", "2:0", "0:1", "1:1", "2:1");

        var missing = chunks.findByWorldIdAndChunkXAndChunkY(fixture.world().getId(), 2, 1).orElseThrow();
        chunks.delete(missing);
        chunks.flush();

        manager.ensureHubChunks(fixture.world());
        assertThat(chunks.findByWorldIdOrderByChunkYAscChunkXAsc(fixture.world().getId())).hasSize(6);
    }

    @Test
    void deterministicallyGeneratesGuaranteedOuterRegionsWithCompleteTerrain() {
        Fixture fixture = fixture("outer-regions");

        var meadow = generation.ensureGenerated(fixture.world().getId(), -1, 0);
        var forest = generation.ensureGenerated(fixture.world().getId(), 3, 0);
        var pond = generation.ensureGenerated(fixture.world().getId(), 1, 2);
        var pondTerrain = pondTerrain(fixture.character().getId());
        var pondId = pond.getId();
        var pondVersion = pond.getGenerationVersion();
        var pondAgain = generation.ensureGenerated(fixture.world().getId(), 1, 2);

        assertThat(meadow.getRegionType()).isEqualTo(WorldChunkRegionType.MEADOW);
        assertThat(forest.getRegionType()).isEqualTo(WorldChunkRegionType.FOREST);
        assertThat(pond.getRegionType()).isEqualTo(WorldChunkRegionType.POND);
        assertThat(terrain.findByCharacterIdAndXBetweenAndYBetweenOrderByYAscXAsc(fixture.character().getId(), -8, -1, 0, 7)).hasSize(64);
        assertThat(terrain.findByCharacterIdAndXBetweenAndYBetweenOrderByYAscXAsc(fixture.character().getId(), 24, 31, 0, 7)).hasSize(64);
        assertThat(terrain.findByCharacterIdAndXBetweenAndYBetweenOrderByYAscXAsc(fixture.character().getId(), 8, 15, 16, 23)).hasSize(64);
        assertThat(terrain.findByCharacterIdAndXBetweenAndYBetweenOrderByYAscXAsc(fixture.character().getId(), 8, 15, 16, 23)).anyMatch(tile -> tile.getTerrainType() == TerrainType.WATER);
        assertThat(terrain.findByCharacterIdAndXBetweenAndYBetweenOrderByYAscXAsc(fixture.character().getId(), 8, 15, 16, 23)).anyMatch(tile -> tile.getTerrainType() == TerrainType.BRIDGE);
        assertThat(pondVersion).isEqualTo(pondAgain.getGenerationVersion());
        assertThat(pondId).isEqualTo(pondAgain.getId());
        assertThat(pondTerrain).containsExactlyElementsOf(pondTerrain(fixture.character().getId()));
    }

    private java.util.List<TerrainSnapshot> pondTerrain(Long characterId) {
        return terrain.findByCharacterIdAndXBetweenAndYBetweenOrderByYAscXAsc(characterId, 8, 15, 16, 23)
                .stream().map(tile -> new TerrainSnapshot(tile.getX(), tile.getY(), tile.getTerrainType()))
                .toList();
    }

    private record TerrainSnapshot(int x, int y, TerrainType terrainType) { }

    private Fixture fixture(String suffix) {
        User user = users.save(new User(
                "chunk-" + suffix + "@example.com",
                passwordEncoder.encode("password123"),
                "chunk-" + suffix));
        Character character = characters.save(Character.create(
                user, "청크", CharacterGender.NONE, HairStyle.PIXEL_CUT,
                "brown", Outfit.ROBE, CharacterJob.WIZARD));
        World world = worlds.saveAndFlush(World.create(character, character.getId()));
        return new Fixture(user, character, world);
    }

    private record Fixture(User user, Character character, World world) {
    }

}
