package com.projecteden.collection;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.projecteden.achievement.repository.*;
import com.projecteden.ai.domain.*;
import com.projecteden.ai.repository.RecognitionRepository;
import com.projecteden.auth.jwt.JwtTokenProvider;
import com.projecteden.character.domain.Character;
import com.projecteden.character.domain.CharacterGender;
import com.projecteden.character.domain.CharacterJob;
import com.projecteden.character.domain.HairStyle;
import com.projecteden.character.domain.Outfit;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.collection.repository.CollectionRepository;
import com.projecteden.collection.service.CollectionService;
import com.projecteden.house.domain.House;
import com.projecteden.house.repository.HouseRepository;
import com.projecteden.inventory.domain.Inventory;
import com.projecteden.inventory.repository.InventoryRepository;
import com.projecteden.photo.domain.Photo;
import com.projecteden.photo.repository.PhotoRepository;
import com.projecteden.resonance.repository.ResonanceRepository;
import com.projecteden.seed.domain.*;
import com.projecteden.seed.repository.SeedRepository;
import com.projecteden.statistics.repository.CharacterStatisticsRepository;
import com.projecteden.title.repository.*;
import com.projecteden.title.service.TitleService;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;
import com.projecteden.evolution.repository.WorldEvolutionRepository;
import com.projecteden.evolution.repository.WorldDecorationRepository;
import com.projecteden.evolution.repository.EvolutionHistoryRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CollectionAchievementIntegrationTests {
	@Autowired MockMvc mockMvc; @Autowired CollectionService collectionService; @Autowired TitleService titleService;
	@Autowired CollectionRepository collections; @Autowired AchievementRepository achievements; @Autowired UserAchievementRepository userAchievements; @Autowired TitleRepository titles; @Autowired UserTitleRepository userTitles; @Autowired CharacterStatisticsRepository statistics;
	@Autowired ResonanceRepository resonances; @Autowired RecognitionRepository recognitions; @Autowired PhotoRepository photos; @Autowired SeedRepository seeds; @Autowired InventoryRepository inventories; @Autowired HouseRepository houses; @Autowired WorldRepository worlds; @Autowired CharacterRepository characters; @Autowired UserRepository users;
	@Autowired PasswordEncoder encoder; @Autowired JwtTokenProvider tokenProvider;
	@Autowired EvolutionHistoryRepository evolutionHistories; @Autowired WorldDecorationRepository worldDecorations; @Autowired WorldEvolutionRepository worldEvolutions;
	private Character character; private String token; private Recognition flowerRecognition;

	@BeforeEach void setUp(){clean();User user=users.save(new User("collection@example.com",encoder.encode("password123"),"collector"));character=characters.save(Character.create(user,"수집가",CharacterGender.NONE,HairStyle.PIXEL_CUT,"brown",Outfit.ROBE,CharacterJob.WIZARD));World world=worlds.save(World.create(character,123L));House house=houses.save(House.create(world));Inventory inventory=inventories.save(Inventory.create(house));seeds.save(Seed.create(inventory,SeedType.FLOWER,5));flowerRecognition=recognition(RecognizedObject.FLOWER,true);token=tokenProvider.generateAccessToken(user);}
	@AfterEach void tearDown(){clean();}

	@Test void resonanceRegistersFirstCollection() throws Exception { resonate(flowerRecognition).andExpect(status().isOk()); assertEquals(1,collections.countByCharacterId(character.getId())); }
	@Test void rediscoveryIncreasesCount(){collectionService.registerDiscovery(character.getId(),RecognizedObject.FLOWER);collectionService.registerDiscovery(character.getId(),RecognizedObject.FLOWER);assertEquals(2,collections.findByCharacterIdAndRecognizedObject(character.getId(),RecognizedObject.FLOWER).orElseThrow().getDiscoveredCount());}
	@Test void unknownIsCollectedAsUncommon(){collectionService.registerDiscovery(character.getId(),RecognizedObject.UNKNOWN);assertEquals(com.projecteden.collection.domain.Rarity.UNCOMMON,collections.findByCharacterIdAndRecognizedObject(character.getId(),RecognizedObject.UNKNOWN).orElseThrow().getRarity());}
	@Test void collectionSummarySucceeds() throws Exception {collectionService.registerDiscovery(character.getId(),RecognizedObject.FLOWER);mockMvc.perform(get("/api/collections/me").header("Authorization",bearer())).andExpect(status().isOk()).andExpect(jsonPath("$.totalCollectableCount").value(6)).andExpect(jsonPath("$.uniqueCollectedCount").value(1)).andExpect(jsonPath("$.completionRate").value(100.0/6.0)).andExpect(jsonPath("$.collections",hasSize(1)));}
	@Test void firstDiscoveryAchievementIsGranted(){collectionService.registerDiscovery(character.getId(),RecognizedObject.FLOWER);assertEquals(true,userAchievements.existsByCharacterIdAndAchievementCode(character.getId(),"FIRST_DISCOVERY"));}
	@Test void threeCollectionsGrantCollectionAchievement(){discover(RecognizedObject.FLOWER,RecognizedObject.TOMATO,RecognizedObject.CARROT);assertEquals(true,userAchievements.existsByCharacterIdAndAchievementCode(character.getId(),"COLLECTION_3"));}
	@Test void tenDiscoveriesGrantTotalAchievement(){for(int i=0;i<10;i++)collectionService.registerDiscovery(character.getId(),RecognizedObject.FLOWER);assertEquals(true,userAchievements.existsByCharacterIdAndAchievementCode(character.getId(),"TOTAL_DISCOVERY_10"));}
	@Test void fiveSameDiscoveriesGrantFocusedAchievement(){for(int i=0;i<5;i++)collectionService.registerDiscovery(character.getId(),RecognizedObject.FLOWER);assertEquals(true,userAchievements.existsByCharacterIdAndAchievementCode(character.getId(),"SAME_OBJECT_5"));}
	@Test void achievementAutomaticallyGrantsTitle(){collectionService.registerDiscovery(character.getId(),RecognizedObject.FLOWER);assertEquals(true,userTitles.existsByCharacterIdAndTitleCode(character.getId(),"FIRST_OBSERVER"));}
	@Test void activeTitleCanBeSet() throws Exception {titleService.grantTitle(character.getId(),"FIRST_OBSERVER");mockMvc.perform(put("/api/titles/me/active").header("Authorization",bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"titleCode\":\"FIRST_OBSERVER\"}")) .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));}
	@Test void unownedTitleCannotBeActive() throws Exception {mockMvc.perform(put("/api/titles/me/active").header("Authorization",bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"titleCode\":\"SMALL_COLLECTOR\"}")) .andExpect(status().isBadRequest());}
	@Test void achievementIsNotGrantedTwice(){collectionService.registerDiscovery(character.getId(),RecognizedObject.FLOWER);collectionService.registerDiscovery(character.getId(),RecognizedObject.FLOWER);assertEquals(1,userAchievements.countByCharacterIdAndAchievementCode(character.getId(),"FIRST_DISCOVERY"));}
	@Test void titleIsNotGrantedTwice(){titleService.grantTitle(character.getId(),"FIRST_OBSERVER");titleService.grantTitle(character.getId(),"FIRST_OBSERVER");assertEquals(1,userTitles.countByCharacterIdAndTitleCode(character.getId(),"FIRST_OBSERVER"));}
	@Test void statisticsCanBeRetrieved() throws Exception {collectionService.registerDiscovery(character.getId(),RecognizedObject.FLOWER);mockMvc.perform(get("/api/statistics/me").header("Authorization",bearer())).andExpect(status().isOk()).andExpect(jsonPath("$.totalDiscoveries").value(1)).andExpect(jsonPath("$.uniqueCollections").value(1)).andExpect(jsonPath("$.totalAchievements").value(1)).andExpect(jsonPath("$.totalTitles").value(1));}
	@Test void apiRequiresJwt() throws Exception {mockMvc.perform(get("/api/collections/me")).andExpect(status().isUnauthorized());}

	private void discover(RecognizedObject... objects){for(RecognizedObject object:objects)collectionService.registerDiscovery(character.getId(),object);}
	private org.springframework.test.web.servlet.ResultActions resonate(Recognition value)throws Exception{return mockMvc.perform(post("/api/resonances").header("Authorization",bearer()).contentType(MediaType.APPLICATION_JSON).content("{\"recognitionId\":"+value.getId()+"}"));}
	private Recognition recognition(RecognizedObject object,boolean recognized){String stored=UUID.randomUUID()+".jpg";Photo photo=photos.save(Photo.create(character,null,"photo.jpg",stored,"image/jpeg",10,"/uploads/photos/"+stored));return recognitions.save(Recognition.create(photo,object,95,recognized));}
	private String bearer(){return "Bearer "+token;}
	private void clean(){evolutionHistories.deleteAll();worldDecorations.deleteAll();worldEvolutions.deleteAll();userTitles.deleteAll();userAchievements.deleteAll();statistics.deleteAll();collections.deleteAll();resonances.deleteAll();recognitions.deleteAll();photos.deleteAll();seeds.deleteAll();inventories.deleteAll();houses.deleteAll();worlds.deleteAll();characters.deleteAll();users.deleteAll();}
}
