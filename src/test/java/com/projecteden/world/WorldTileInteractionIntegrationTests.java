package com.projecteden.world;

import com.projecteden.world.domain.World;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.projecteden.auth.jwt.JwtTokenProvider;
import com.projecteden.character.domain.Character;
import com.projecteden.character.domain.CharacterGender;
import com.projecteden.character.domain.CharacterJob;
import com.projecteden.character.domain.HairStyle;
import com.projecteden.character.domain.Outfit;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.world.ecology.TileInteractionResponse;
import com.projecteden.world.ecology.TileInteractionCategory;
import com.projecteden.world.ecology.TileInteractionType;
import com.projecteden.world.ecology.HabitatType;
import com.projecteden.world.ecology.TerrainType;
import com.projecteden.world.ecology.WorldAssetType;
import com.projecteden.world.ecology.WorldCategory;
import com.projecteden.world.ecology.WorldChange;
import com.projecteden.world.ecology.WorldChangeRepository;
import com.projecteden.world.ecology.WorldEcologyService;
import com.projecteden.world.ecology.WorldPlacedObject;
import com.projecteden.world.ecology.WorldPlacedObjectRepository;
import com.projecteden.world.ecology.WorldPlayerPosition;
import com.projecteden.world.ecology.WorldPlayerPositionRepository;
import com.projecteden.world.ecology.WorldStateResponse;
import com.projecteden.world.ecology.MapBoundsResponse;
import com.projecteden.world.ecology.PlayerPositionResponse;
import com.projecteden.world.generation.ChunkGenerationService;
import com.projecteden.world.repository.WorldRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WorldTileInteractionIntegrationTests {

	private static final List<TemplateNpcFixture> TEMPLATE_NPCS = List.of(
			new TemplateNpcFixture(WorldAssetType.DEFAULT_NPC_GUIDE, "마을 안내자", 10, 6),
			new TemplateNpcFixture(WorldAssetType.DEFAULT_NPC_GARDENER, "정원 관리인", 5, 8),
			new TemplateNpcFixture(WorldAssetType.DEFAULT_NPC_MEMORY_KEEPER, "기억 보관인", 12, 9),
			new TemplateNpcFixture(WorldAssetType.DEFAULT_NPC_ANIMAL_CARETAKER, "동물 돌봄이", 16, 8));
	private static final List<ContextualFixture> READ_ONLY_CONTEXTS = List.of(
			new ContextualFixture(WorldAssetType.COMMUNITY_HOUSE, TileInteractionCategory.COMMUNITY,
					"마을 회관", "둘러보기", 14, 6),
			new ContextualFixture(WorldAssetType.DEFAULT_DOG, TileInteractionCategory.ANIMAL,
					"강아지", "다가가기", 17, 9),
			new ContextualFixture(WorldAssetType.DEFAULT_CAT, TileInteractionCategory.ANIMAL,
					"고양이", "다가가기", 18, 9),
			new ContextualFixture(WorldAssetType.DEFAULT_BIRD, TileInteractionCategory.ANIMAL,
					"새", "다가가기", 19, 8));

	@Autowired
	private WorldEcologyService worldEcologyService;
	@Autowired
	private ChunkGenerationService chunkGenerationService;
	@Autowired
	private WorldRepository worldRepository;

	@Autowired
	private WorldPlayerPositionRepository positions;

	@Autowired
	private WorldChangeRepository changes;

	@Autowired
	private WorldPlacedObjectRepository placedObjects;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CharacterRepository characterRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void calculatesStableCardinalInteractionsFromThePersistedCentralPosition() {
		User user = createUser("center");
		Character character = createCharacter(user);
		movePersistedPosition(character, 2, 2);

		WorldStateResponse state = worldEcologyService.stateForUser(user.getId());

		assertThat(state.playerPosition().x()).isEqualTo(2);
		assertThat(state.playerPosition().y()).isEqualTo(2);
		assertThat(state.availableInteractions())
				.extracting(interaction -> coordinate(interaction))
				.containsExactly("2,1", "1,2", "3,2", "2,3");
		assertThat(state.availableInteractions())
				.allSatisfy(interaction -> {
					assertThat(interaction.type()).isEqualTo(TileInteractionType.INSPECT);
					assertThat(interaction.available()).isTrue();
					assertThat(interaction.reason()).isNull();
				});
		assertThat(state.availableInteractions()).extracting(WorldTileInteractionIntegrationTests::coordinate)
				.doesNotContain("1,1", "3,1", "1,3", "3,3")
				.doesNotHaveDuplicates();
	}

	@Test
	void excludesOutOfBoundsAndDiagonalCoordinatesAtCornersAndEdges() {
		User user = createUser("bounds");
		Character character = createCharacter(user);
		worldRepository.saveAndFlush(World.create(character, character.getId()));
		worldEcologyService.stateForUser(user.getId());
		World world = worldRepository.findByCharacterId(character.getId()).orElseThrow();

		chunkGenerationService.ensureGenerated(world.getId(), -1, -1);
		movePersistedPosition(character, World.DEFAULT_MIN_TILE_X, World.DEFAULT_MIN_TILE_Y);
		assertThat(coordinates(worldEcologyService.stateForUser(user.getId()).availableInteractions()))
				.containsExactly("-7,-8", "-8,-7")
				.doesNotContain("-9,-8", "-8,-9", "-9,-9", "-7,-7");

		chunkGenerationService.ensureGenerated(world.getId(), 3, 2);
		movePersistedPosition(character, World.DEFAULT_MAX_TILE_X, World.DEFAULT_MAX_TILE_Y);
		assertThat(coordinates(worldEcologyService.stateForUser(user.getId()).availableInteractions()))
				.containsExactly("31,22", "30,23")
				.doesNotContain("31,24", "32,23", "30,22");

		chunkGenerationService.ensureGenerated(world.getId(), 0, -1);
		movePersistedPosition(character, 5, World.DEFAULT_MIN_TILE_Y);
		assertThat(coordinates(worldEcologyService.stateForUser(user.getId()).availableInteractions()))
				.containsExactly("4,-8", "6,-8", "5,-7");
	}

	@Test
	void bootstrapsMissingPositionAndKeepsEachUsersInteractionsAuthoritativeAndIsolated() {
		User firstUser = createUser("first");
		Character firstCharacter = createCharacter(firstUser);
		User secondUser = createUser("second");
		Character secondCharacter = createCharacter(secondUser);

		WorldStateResponse initial = worldEcologyService.stateForUser(firstUser.getId());
		assertThat(initial.playerPosition().x()).isEqualTo(11);
		assertThat(initial.playerPosition().y()).isEqualTo(8);
		assertThat(initial.availableInteractions()).hasSize(4);

		movePersistedPosition(firstCharacter, 2, 2);
		movePersistedPosition(secondCharacter, 20, 14);

		assertThat(coordinates(worldEcologyService.stateForUser(firstUser.getId()).availableInteractions()))
				.containsExactly("2,1", "1,2", "3,2", "2,3");
		assertThat(coordinates(worldEcologyService.stateForUser(secondUser.getId()).availableInteractions()))
				.containsExactly("20,13", "19,14", "21,14", "20,15");
	}

	@Test
	void serializesAWorldStateWithoutLegacyWorldCreationUsingTheAdditiveInteractionContract() throws Exception {
		User user = createUser("json");
		createCharacter(user);
		String token = jwtTokenProvider.generateAccessToken(user);

		mockMvc.perform(get("/api/worlds/me/state")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.terrainTiles").isArray())
				.andExpect(jsonPath("$.playerPosition.x").value(11))
				.andExpect(jsonPath("$.playerPosition.y").value(8))
				.andExpect(jsonPath("$.availableInteractions").isArray())
				.andExpect(jsonPath("$.availableInteractions.length()").value(4))
				.andExpect(jsonPath("$.availableInteractions[0].x").value(11))
				.andExpect(jsonPath("$.availableInteractions[0].y").value(7))
				.andExpect(jsonPath("$.availableInteractions[0].type").value("INSPECT"))
				.andExpect(jsonPath("$.availableInteractions[0].available").value(true))
				.andExpect(jsonPath("$.availableInteractions[0].reason").doesNotExist());
	}

	@Test
	void exposesTypedTalkOnlyWhenThePlayerIsAdjacentToATemplateNpc() {
		User user = createUser("talk");
		Character character = createCharacter(user);
		movePersistedPosition(character, 10, 7); // directly south of TEMPLATE_GUIDE at 10,6

		WorldStateResponse adjacent = worldEcologyService.stateForUser(user.getId());
		assertThat(adjacent.npcPositions()).hasSize(4);
		assertThat(adjacent.availableInteractions())
				.filteredOn(interaction -> interaction.type() == TileInteractionType.TALK)
				.singleElement()
				.satisfies(interaction -> {
					assertThat(interaction.targetId()).isNotNull();
					assertThat(interaction.targetAssetType().name()).isEqualTo("DEFAULT_NPC_GUIDE");
					assertThat(interaction.displayName()).isEqualTo("마을 안내자");
					assertThat(coordinate(interaction)).isEqualTo("10,6");
				});

		movePersistedPosition(character, 2, 2);
		assertThat(worldEcologyService.stateForUser(user.getId()).availableInteractions())
				.noneMatch(interaction -> interaction.type() == TileInteractionType.TALK);
	}

	@Test
	void exposesExactlyOneCardinalTalkForEveryTemplateNpcUsingThePersistedPosition() {
		User user = createUser("all-template-npcs");
		Character character = createCharacter(user);
		WorldStateResponse bootstrapped = worldEcologyService.stateForUser(user.getId());

		for (TemplateNpcFixture npc : TEMPLATE_NPCS) {
			Long expectedTargetId = bootstrapped.placedObjects().stream()
					.filter(object -> object.assetType() == npc.assetType())
					.findFirst()
					.orElseThrow()
					.id();
			for (int[] direction : List.of(
					new int[] {0, -1},
					new int[] {0, 1},
					new int[] {-1, 0},
					new int[] {1, 0})) {
				movePersistedPosition(character, npc.x() + direction[0], npc.y() + direction[1]);

				assertThat(worldEcologyService.stateForUser(user.getId()).availableInteractions())
						.filteredOn(interaction -> interaction.type() == TileInteractionType.TALK
								&& interaction.targetAssetType() == npc.assetType())
						.singleElement()
						.satisfies(interaction -> {
							assertThat(interaction.targetId()).isEqualTo(expectedTargetId);
							assertThat(interaction.displayName()).isEqualTo(npc.displayName());
							assertThat(interaction.x()).isEqualTo(npc.x());
							assertThat(interaction.y()).isEqualTo(npc.y());
						});
			}
		}
	}

	@Test
	void excludesDiagonalAndOutOfRangeTalkForEveryTemplateNpc() {
		User user = createUser("template-npc-range");
		Character character = createCharacter(user);
		worldEcologyService.stateForUser(user.getId());

		for (TemplateNpcFixture npc : TEMPLATE_NPCS) {
			movePersistedPosition(character, npc.x() + 1, npc.y() + 1);
			assertThat(worldEcologyService.stateForUser(user.getId()).availableInteractions())
					.noneMatch(interaction -> interaction.type() == TileInteractionType.TALK
							&& interaction.targetAssetType() == npc.assetType());

			movePersistedPosition(character, npc.x() + 2, npc.y());
			assertThat(worldEcologyService.stateForUser(user.getId()).availableInteractions())
					.noneMatch(interaction -> interaction.type() == TileInteractionType.TALK
							&& interaction.targetAssetType() == npc.assetType());
		}
	}

	@Test
	void serializesTalkFromTheStoredPlayerPositionWithoutAcceptingClientRangeHints() throws Exception {
		User user = createUser("stored-talk-position");
		Character character = createCharacter(user);
		movePersistedPosition(character, 10, 7);
		String token = jwtTokenProvider.generateAccessToken(user);

		mockMvc.perform(get("/api/worlds/me/state")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.playerPosition.x").value(10))
				.andExpect(jsonPath("$.playerPosition.y").value(7))
				.andExpect(jsonPath("$.availableInteractions[0].type").value("TALK"))
				.andExpect(jsonPath("$.availableInteractions[0].targetAssetType").value("DEFAULT_NPC_GUIDE"))
				.andExpect(jsonPath("$.availableInteractions[0].displayName").value("마을 안내자"));
	}

	@Test
	void keepsTemplateNpcTalkTargetsOwnedAndIsolatedPerCharacter() {
		User firstUser = createUser("npc-owner-first");
		Character firstCharacter = createCharacter(firstUser);
		User secondUser = createUser("npc-owner-second");
		Character secondCharacter = createCharacter(secondUser);

		for (TemplateNpcFixture npc : TEMPLATE_NPCS) {
			movePersistedPosition(firstCharacter, npc.x(), npc.y() + 1);
			movePersistedPosition(secondCharacter, npc.x(), npc.y() + 1);

			TileInteractionResponse firstTalk = talkFor(firstUser, npc.assetType());
			TileInteractionResponse secondTalk = talkFor(secondUser, npc.assetType());

			assertThat(firstTalk.targetId()).isNotEqualTo(secondTalk.targetId());
			assertThat(worldEcologyService.stateForUser(firstUser.getId()).availableInteractions())
					.extracting(TileInteractionResponse::targetId)
					.doesNotContain(secondTalk.targetId());
			assertThat(worldEcologyService.stateForUser(secondUser.getId()).availableInteractions())
					.extracting(TileInteractionResponse::targetId)
					.doesNotContain(firstTalk.targetId());
		}
	}

	@Test
	void exposesStableEmptyFarmContextWithoutReplacingTheExistingInspectContract() {
		User user = createUser("empty-farm");
		Character character = createCharacter(user);

		TileInteractionResponse interaction = contextualAt(user, character, 3, 8, WorldAssetType.FARM_PLOT_EMPTY);

		assertContext(interaction, TileInteractionCategory.FARM, "비어 있는 밭", "살펴보기");
		assertThat(interaction.targetId()).isNotNull();
		assertThat(worldEcologyService.stateForUser(user.getId()).availableInteractions())
				.filteredOn(candidate -> coordinate(candidate).equals("3,9"))
				.singleElement()
				.extracting(TileInteractionResponse::type)
				.isEqualTo(TileInteractionType.INTERACT);
	}

	@Test
	void exposesEverySupportedCropAsTheSameStableCropCategory() {
		User user = createUser("crops");
		Character character = createCharacter(user);
		worldEcologyService.stateForUser(user.getId());
		addContextualObject(character, "TEST_VEGETABLE", WorldAssetType.FARM_VEGETABLE, 6, 9);

		assertContext(contextualAt(user, character, 2, 11, WorldAssetType.FARM_CARROT),
				TileInteractionCategory.CROP, "당근밭", "작물 살펴보기");
		assertContext(contextualAt(user, character, 3, 3, WorldAssetType.FARM_FLOWER),
				TileInteractionCategory.CROP, "꽃밭", "작물 살펴보기");
		assertContext(contextualAt(user, character, 7, 9, WorldAssetType.FARM_TOMATO),
				TileInteractionCategory.CROP, "토마토밭", "작물 살펴보기");
		assertContext(contextualAt(user, character, 9, 9, WorldAssetType.FARM_CABBAGE),
				TileInteractionCategory.CROP, "양배추밭", "작물 살펴보기");
		assertContext(contextualAt(user, character, 6, 8, WorldAssetType.FARM_VEGETABLE),
				TileInteractionCategory.CROP, "채소밭", "작물 살펴보기");
	}

	@Test
	void exposesAllDefaultAnimalAssetsAsContextualAnimalInteractions() {
		User user = createUser("animals");
		Character character = createCharacter(user);

		assertContext(contextualAt(user, character, 17, 10, WorldAssetType.DEFAULT_DOG),
				TileInteractionCategory.ANIMAL, "강아지", "다가가기");
		assertContext(contextualAt(user, character, 18, 10, WorldAssetType.DEFAULT_CAT),
				TileInteractionCategory.ANIMAL, "고양이", "다가가기");
		assertContext(contextualAt(user, character, 19, 9, WorldAssetType.DEFAULT_BIRD),
				TileInteractionCategory.ANIMAL, "새", "다가가기");
	}

	@Test
	void exposesCommunityHouseAsAContextualCommunityInteraction() {
		User user = createUser("community");
		Character character = createCharacter(user);

		assertContext(contextualAt(user, character, 14, 7, WorldAssetType.COMMUNITY_HOUSE),
				TileInteractionCategory.COMMUNITY, "마을 회관", "둘러보기");
	}

	@Test
	void exposesCommunityHouseOnlyFromItsFrontDoorApproach() {
		User user = createUser("community-entrance");
		Character character = createCharacter(user);
		WorldStateResponse state = worldEcologyService.stateForUser(user.getId());
		Long houseId = state.placedObjects().stream()
				.filter(object -> object.assetType() == WorldAssetType.COMMUNITY_HOUSE)
				.findFirst().orElseThrow().id();

		for (int[] position : List.of(new int[] {13, 6}, new int[] {15, 6}, new int[] {14, 5})) {
			movePersistedPosition(character, position[0], position[1]);
			assertThat(worldEcologyService.stateForUser(user.getId()).availableInteractions())
					.noneMatch(interaction -> interaction.type() == TileInteractionType.INTERACT
							&& Objects.equals(interaction.targetId(), houseId));
		}
	}

	@Test
	void exposesExactReadOnlyContextFromEveryCardinalDirectionWithoutDuplicates() {
		User user = createUser("read-only-cardinal");
		Character character = createCharacter(user);
		WorldStateResponse bootstrapped = worldEcologyService.stateForUser(user.getId());

		for (ContextualFixture fixture : READ_ONLY_CONTEXTS) {
			Long expectedTargetId = bootstrapped.placedObjects().stream()
					.filter(object -> object.assetType() == fixture.assetType()
							&& object.x() / 48 == fixture.x()
							&& object.y() / 48 == fixture.y())
					.findFirst()
					.orElseThrow()
					.id();
			List<int[]> directions = fixture.assetType() == WorldAssetType.COMMUNITY_HOUSE
					? List.of(new int[] {0, 1})
					: List.of(new int[] {0, -1}, new int[] {0, 1}, new int[] {-1, 0}, new int[] {1, 0});
			for (int[] direction : directions) {
				movePersistedPosition(character, fixture.x() + direction[0], fixture.y() + direction[1]);

				assertThat(worldEcologyService.stateForUser(user.getId()).availableInteractions())
						.filteredOn(interaction -> interaction.type() == TileInteractionType.INTERACT
								&& interaction.targetAssetType() == fixture.assetType())
						.singleElement()
						.satisfies(interaction -> {
							assertThat(interaction.targetId()).isEqualTo(expectedTargetId);
							assertThat(interaction.category()).isEqualTo(fixture.category());
							assertThat(interaction.displayName()).isEqualTo(fixture.displayName());
							assertThat(interaction.actionLabel()).isEqualTo(fixture.actionLabel());
							assertThat(interaction.x()).isEqualTo(fixture.x());
							assertThat(interaction.y()).isEqualTo(fixture.y());
						});
			}
		}
	}

	@Test
	void excludesDiagonalAndOutOfRangeReadOnlyContextForEveryAnimalAndCommunityAsset() {
		User user = createUser("read-only-range");
		Character character = createCharacter(user);
		WorldStateResponse bootstrapped = worldEcologyService.stateForUser(user.getId());

		for (ContextualFixture fixture : READ_ONLY_CONTEXTS) {
			Long expectedTargetId = bootstrapped.placedObjects().stream()
					.filter(object -> object.assetType() == fixture.assetType()
							&& object.x() / 48 == fixture.x()
							&& object.y() / 48 == fixture.y())
					.findFirst()
					.orElseThrow()
					.id();
			movePersistedPosition(character, fixture.x() + 1, fixture.y() + 1);
			assertThat(worldEcologyService.stateForUser(user.getId()).availableInteractions())
					.noneMatch(interaction -> interaction.type() == TileInteractionType.INTERACT
							&& Objects.equals(interaction.targetId(), expectedTargetId));

			movePersistedPosition(character, fixture.x() + 2, fixture.y());
			assertThat(worldEcologyService.stateForUser(user.getId()).availableInteractions())
					.noneMatch(interaction -> interaction.type() == TileInteractionType.INTERACT
							&& Objects.equals(interaction.targetId(), expectedTargetId));
		}
	}

	@Test
	void keepsEveryAnimalAndCommunityTargetOwnedAndIsolatedPerCharacter() {
		User firstUser = createUser("read-only-owner-first");
		Character firstCharacter = createCharacter(firstUser);
		User secondUser = createUser("read-only-owner-second");
		Character secondCharacter = createCharacter(secondUser);

		for (ContextualFixture fixture : READ_ONLY_CONTEXTS) {
			movePersistedPosition(firstCharacter, fixture.x(), fixture.y() + 1);
			movePersistedPosition(secondCharacter, fixture.x(), fixture.y() + 1);

			TileInteractionResponse first = contextualFor(firstUser, fixture.assetType());
			TileInteractionResponse second = contextualFor(secondUser, fixture.assetType());

			assertThat(first.targetId()).isNotEqualTo(second.targetId());
			assertThat(worldEcologyService.stateForUser(firstUser.getId()).availableInteractions())
					.extracting(TileInteractionResponse::targetId)
					.doesNotContain(second.targetId());
			assertThat(worldEcologyService.stateForUser(secondUser.getId()).availableInteractions())
					.extracting(TileInteractionResponse::targetId)
					.doesNotContain(first.targetId());
		}
	}

	@Test
	void resolvesTalkBeforeContextualInteractionWhenCandidatesShareATile() {
		User user = createUser("priority");
		Character character = createCharacter(user);
		worldEcologyService.stateForUser(user.getId());
		for (TemplateNpcFixture npc : TEMPLATE_NPCS) {
			addContextualObject(character, "TEST_CONTEXT_ON_" + npc.assetType(),
					WorldAssetType.FARM_PLOT_EMPTY, npc.x(), npc.y());
			movePersistedPosition(character, npc.x(), npc.y() + 1);

			assertThat(worldEcologyService.stateForUser(user.getId()).availableInteractions())
					.filteredOn(interaction -> coordinate(interaction).equals(npc.x() + "," + npc.y()))
					.singleElement()
					.satisfies(interaction -> {
						assertThat(interaction.type()).isEqualTo(TileInteractionType.TALK);
						assertThat(interaction.targetAssetType()).isEqualTo(npc.assetType());
						assertThat(interaction.category()).isNull();
					});
		}
	}

	@Test
	void ordersInteractionsByPriorityThenCoordinatesDeterministically() {
		User user = createUser("ordering");
		Character character = createCharacter(user);
		movePersistedPosition(character, 18, 8);

		List<TileInteractionResponse> first = worldEcologyService.stateForUser(user.getId()).availableInteractions();
		List<TileInteractionResponse> second = worldEcologyService.stateForUser(user.getId()).availableInteractions();

		assertThat(first).isEqualTo(second);
		assertThat(first).extracting(TileInteractionResponse::type)
				.containsExactly(TileInteractionType.INTERACT, TileInteractionType.INTERACT,
						TileInteractionType.INSPECT, TileInteractionType.INSPECT);
		assertThat(first).extracting(WorldTileInteractionIntegrationTests::coordinate)
				.containsExactly("19,8", "18,9", "18,7", "17,8");
	}

	@Test
	void keepsContextualTargetsIsolatedByAuthenticatedUsersCharacter() {
		User firstUser = createUser("context-first");
		Character firstCharacter = createCharacter(firstUser);
		User secondUser = createUser("context-second");
		Character secondCharacter = createCharacter(secondUser);

		Long firstTarget = contextualAt(firstUser, firstCharacter, 3, 8, WorldAssetType.FARM_PLOT_EMPTY).targetId();
		Long secondTarget = contextualAt(secondUser, secondCharacter, 3, 8, WorldAssetType.FARM_PLOT_EMPTY).targetId();

		assertThat(firstTarget).isNotEqualTo(secondTarget);
		assertThat(worldEcologyService.stateForUser(firstUser.getId()).availableInteractions())
				.extracting(TileInteractionResponse::targetId)
				.doesNotContain(secondTarget);
	}

	@Test
	void repeatedBootstrapNeverDuplicatesContextualTargetsOrTiles() {
		User user = createUser("context-idempotent");
		Character character = createCharacter(user);
		movePersistedPosition(character, 2, 11);

		List<TileInteractionResponse> first = worldEcologyService.stateForUser(user.getId()).availableInteractions();
		List<TileInteractionResponse> second = worldEcologyService.stateForUser(user.getId()).availableInteractions();

		assertThat(second).isEqualTo(first);
		assertThat(second).extracting(WorldTileInteractionIntegrationTests::coordinate).doesNotHaveDuplicates();
		assertThat(second.stream().filter(interaction -> interaction.targetAssetType() == WorldAssetType.FARM_CARROT))
				.hasSize(1);
	}

	@Test
	void serializesContextualInteractionMetadataThroughTheExistingWorldStateEndpoint() throws Exception {
		User user = createUser("context-json");
		Character character = createCharacter(user);
		movePersistedPosition(character, 3, 8);
		String token = jwtTokenProvider.generateAccessToken(user);

		mockMvc.perform(get("/api/worlds/me/state").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableInteractions[?(@.targetAssetType == 'FARM_PLOT_EMPTY')].type").value("INTERACT"))
				.andExpect(jsonPath("$.availableInteractions[?(@.targetAssetType == 'FARM_PLOT_EMPTY')].category").value("FARM"))
				.andExpect(jsonPath("$.availableInteractions[?(@.targetAssetType == 'FARM_PLOT_EMPTY')].displayName").value("비어 있는 밭"))
				.andExpect(jsonPath("$.availableInteractions[?(@.targetAssetType == 'FARM_PLOT_EMPTY')].actionLabel").value("살펴보기"));
	}

	@Test
	void serializesAnExplicitEmptyInteractionCollectionAsAnEmptyJsonArray() throws Exception {
		WorldStateResponse state = new WorldStateResponse(
				List.of(), List.of(), List.of(), new MapBoundsResponse(0, 23, 0, 15),
				new PlayerPositionResponse(0, 0), List.of(), List.of(), List.of(), List.of(),
				"LIVING_VILLAGE", "테스트 마을");

		var json = objectMapper.readTree(objectMapper.writeValueAsString(state));

		assertThat(json.path("availableInteractions").isArray()).isTrue();
		assertThat(json.path("availableInteractions")).isEmpty();
		assertThat(json.path("terrainTiles").isArray()).isTrue();
	}

	private User createUser(String suffix) {
		return userRepository.save(new User(
				"interaction-" + suffix + "@example.com",
				passwordEncoder.encode("password123"),
				"eden-" + suffix));
	}

	private Character createCharacter(User user) {
		return characterRepository.save(Character.create(
				user,
				"에덴",
				CharacterGender.NONE,
				HairStyle.PIXEL_CUT,
				"brown",
				Outfit.ROBE,
				CharacterJob.WIZARD));
	}

	private void movePersistedPosition(Character character, int x, int y) {
		WorldPlayerPosition position = positions.findByCharacterId(character.getId())
				.orElseGet(() -> positions.save(WorldPlayerPosition.create(character, x, y)));
		position.moveTo(x, y);
	}

	private TileInteractionResponse contextualAt(
			User user,
			Character character,
			int playerX,
			int playerY,
			WorldAssetType targetAssetType) {
		movePersistedPosition(character, playerX, playerY);
		return worldEcologyService.stateForUser(user.getId()).availableInteractions().stream()
				.filter(interaction -> interaction.targetAssetType() == targetAssetType)
				.findFirst()
				.orElseThrow();
	}

	private void addContextualObject(
			Character character,
			String key,
			WorldAssetType assetType,
			int tileX,
			int tileY) {
		WorldChange change = changes.save(WorldChange.template(
				character, WorldCategory.NATURE, assetType, key, "테스트 contextual object", tileX * 48, tileY * 48));
		placedObjects.save(WorldPlacedObject.create(
				change, assetType, TerrainType.GRASS, HabitatType.DECORATION_ONLY, tileX * 48, tileY * 48));
	}

	private static void assertContext(
			TileInteractionResponse interaction,
			TileInteractionCategory category,
			String displayName,
			String actionLabel) {
		assertThat(interaction.type()).isEqualTo(TileInteractionType.INTERACT);
		assertThat(interaction.category()).isEqualTo(category);
		assertThat(interaction.displayName()).isEqualTo(displayName);
		assertThat(interaction.actionLabel()).isEqualTo(actionLabel);
	}

	private static List<String> coordinates(List<TileInteractionResponse> interactions) {
		return interactions.stream().map(WorldTileInteractionIntegrationTests::coordinate).toList();
	}

	private TileInteractionResponse talkFor(User user, WorldAssetType assetType) {
		return worldEcologyService.stateForUser(user.getId()).availableInteractions().stream()
				.filter(interaction -> interaction.type() == TileInteractionType.TALK
						&& interaction.targetAssetType() == assetType)
				.findFirst()
				.orElseThrow();
	}

	private TileInteractionResponse contextualFor(User user, WorldAssetType assetType) {
		return worldEcologyService.stateForUser(user.getId()).availableInteractions().stream()
				.filter(interaction -> interaction.type() == TileInteractionType.INTERACT
						&& interaction.targetAssetType() == assetType)
				.findFirst()
				.orElseThrow();
	}

	private static String coordinate(TileInteractionResponse interaction) {
		return interaction.x() + "," + interaction.y();
	}

	private record TemplateNpcFixture(
			WorldAssetType assetType,
			String displayName,
			int x,
			int y) {
	}

	private record ContextualFixture(
			WorldAssetType assetType,
			TileInteractionCategory category,
			String displayName,
			String actionLabel,
			int x,
			int y) {
	}
}
