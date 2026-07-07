package com.projecteden.evolution;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.security.crypto.password.PasswordEncoder;import org.springframework.test.context.ActiveProfiles;import org.springframework.test.web.servlet.MockMvc;
import com.projecteden.auth.jwt.JwtTokenProvider;import com.projecteden.character.domain.Character;import com.projecteden.character.domain.CharacterGender;import com.projecteden.character.domain.CharacterJob;import com.projecteden.character.domain.HairStyle;import com.projecteden.character.domain.Outfit;import com.projecteden.character.repository.CharacterRepository;import com.projecteden.evolution.domain.*;import com.projecteden.evolution.repository.*;import com.projecteden.evolution.service.EvolutionService;import com.projecteden.user.domain.User;import com.projecteden.user.repository.UserRepository;

@SpringBootTest@AutoConfigureMockMvc@ActiveProfiles("test")class EvolutionIntegrationTests{
	@Autowired MockMvc mockMvc;@Autowired EvolutionService service;@Autowired WorldEvolutionRepository evolutions;@Autowired WorldDecorationRepository decorations;@Autowired EvolutionHistoryRepository histories;@Autowired CharacterRepository characters;@Autowired UserRepository users;@Autowired PasswordEncoder encoder;@Autowired JwtTokenProvider tokens;
	private Character character;private String token;
	@BeforeEach void setUp(){clean();User user=users.save(new User("evolution@example.com",encoder.encode("password123"),"evolver"));character=characters.save(Character.create(user,"진화자",CharacterGender.NONE,HairStyle.PIXEL_CUT,"brown",Outfit.ROBE,CharacterJob.WIZARD));token=tokens.generateAccessToken(user);}@AfterEach void tearDown(){clean();}

	@Test void firstGetCreatesDefaultEvolution(){var value=service.getMyEvolution(character.getId());assertEquals(1,value.worldLevel());assertEquals(0,value.evolutionPoint());assertEquals(WorldStage.SEED,value.worldStage());}
	@Test void recognitionAddsFivePoints(){assertPoint(EvolutionSourceType.RECOGNITION,5);}
	@Test void resonanceAddsTenPoints(){assertPoint(EvolutionSourceType.RESONANCE,10);}
	@Test void achievementAddsThirtyPoints(){assertPoint(EvolutionSourceType.ACHIEVEMENT,30);}
	@Test void cheerAddsTwoPoints(){assertPoint(EvolutionSourceType.CHEER,2);}
	@Test void hundredPointsReachesLevelTwo(){addTo(100);assertLevel(2,WorldStage.SPROUT);}
	@Test void twoHundredFiftyPointsReachesLevelThree(){addTo(250);assertLevel(3,WorldStage.GARDEN);}
	@Test void fiveHundredPointsReachesLevelFour(){addTo(500);assertLevel(4,WorldStage.FOREST);}
	@Test void thousandPointsReachesLevelFive(){addTo(1000);assertLevel(5,WorldStage.PARADISE);}
	@Test void levelTwoUnlocksFlowerField(){addTo(100);assertDecoration(DecorationType.FLOWER_FIELD);}
	@Test void levelThreeUnlocksTree(){addTo(250);assertDecoration(DecorationType.TREE);}
	@Test void levelFourUnlocksBenchAndRoad(){addTo(500);assertDecoration(DecorationType.BENCH);assertDecoration(DecorationType.ROAD);}
	@Test void levelFiveUnlocksFinalDecorations(){addTo(1000);assertDecoration(DecorationType.LAMP);assertDecoration(DecorationType.FOUNTAIN);assertDecoration(DecorationType.WINDMILL);}
	@Test void decorationsAreNotDuplicated(){addTo(100);service.addEvolutionPoint(character.getId(),EvolutionSourceType.CHEER);assertEquals(1,decorations.findByCharacterId(character.getId()).stream().filter(v->v.getDecorationType()==DecorationType.FLOWER_FIELD).count());}
	@Test void pointGainHistoryIsSaved(){service.addEvolutionPoint(character.getId(),EvolutionSourceType.RECOGNITION);assertHistory(EvolutionEventType.POINT_GAIN);}
	@Test void levelUpHistoryIsSaved(){addTo(100);assertHistory(EvolutionEventType.LEVEL_UP);}
	@Test void stageUpHistoryIsSaved(){addTo(100);assertHistory(EvolutionEventType.STAGE_UP);}
	@Test void decorationUnlockHistoryIsSaved(){addTo(100);assertHistory(EvolutionEventType.DECORATION_UNLOCK);}
	@Test void evolutionApiSucceeds()throws Exception{mockMvc.perform(get("/api/evolution/me").header("Authorization",bearer())).andExpect(status().isOk()).andExpect(jsonPath("$.worldLevel").value(1)).andExpect(jsonPath("$.worldStage").value("SEED"));}
	@Test void historyApiSucceeds()throws Exception{service.addEvolutionPoint(character.getId(),EvolutionSourceType.RECOGNITION);mockMvc.perform(get("/api/evolution/history").header("Authorization",bearer())).andExpect(status().isOk()).andExpect(jsonPath("$[0].eventType").value("POINT_GAIN"));}
	@Test void decorationApiSucceeds()throws Exception{addTo(100);mockMvc.perform(get("/api/evolution/decorations").header("Authorization",bearer())).andExpect(status().isOk()).andExpect(jsonPath("$[*].decorationType",hasItem("FLOWER_FIELD")));}
	@Test void apiRequiresJwt()throws Exception{mockMvc.perform(get("/api/evolution/me")).andExpect(status().isUnauthorized());}

	private void assertPoint(EvolutionSourceType source,int expected){assertEquals(expected,service.addEvolutionPoint(character.getId(),source).evolutionPoint());}private void assertLevel(int level,WorldStage stage){var value=service.getMyEvolution(character.getId());assertEquals(level,value.worldLevel());assertEquals(stage,value.worldStage());}private void assertDecoration(DecorationType type){assertEquals(true,decorations.existsByCharacterIdAndDecorationType(character.getId(),type));}private void assertHistory(EvolutionEventType type){assertEquals(true,histories.findByCharacterIdOrderByCreatedAtDesc(character.getId()).stream().anyMatch(value->value.getEventType()==type));}
	private void addTo(int target){int remaining=target;while(remaining>=30){service.addEvolutionPoint(character.getId(),EvolutionSourceType.ACHIEVEMENT);remaining-=30;}while(remaining>=10){service.addEvolutionPoint(character.getId(),EvolutionSourceType.RESONANCE);remaining-=10;}while(remaining>=5){service.addEvolutionPoint(character.getId(),EvolutionSourceType.RECOGNITION);remaining-=5;}while(remaining>=2){service.addEvolutionPoint(character.getId(),EvolutionSourceType.CHEER);remaining-=2;}if(remaining!=0)throw new IllegalArgumentException("표현할 수 없는 테스트 포인트입니다.");}
	private String bearer(){return "Bearer "+token;}private void clean(){histories.deleteAll();decorations.deleteAll();evolutions.deleteAll();characters.deleteAll();users.deleteAll();}
}
