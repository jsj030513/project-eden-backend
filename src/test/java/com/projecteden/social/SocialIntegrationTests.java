package com.projecteden.social;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
import com.projecteden.cheer.repository.CheerRepository;
import com.projecteden.friend.domain.Friend;
import com.projecteden.friend.dto.FriendRequestDTO;
import com.projecteden.friend.repository.FriendRepository;
import com.projecteden.friend.service.FriendService;
import com.projecteden.notification.domain.NotificationType;
import com.projecteden.notification.repository.NotificationRepository;
import com.projecteden.notification.service.NotificationService;
import com.projecteden.penalty.repository.DailyPenaltyRepository;
import com.projecteden.profile.repository.ProfileRepository;
import com.projecteden.ranking.domain.Ranking;
import com.projecteden.ranking.repository.RankingRepository;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.visit.repository.IslandVisitRepository;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;

import java.time.LocalDateTime;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SocialIntegrationTests {
	@Autowired MockMvc mockMvc;
	@Autowired UserRepository users;
	@Autowired CharacterRepository characters;
	@Autowired WorldRepository worlds;
	@Autowired FriendRepository friends;
	@Autowired IslandVisitRepository visits;
	@Autowired CheerRepository cheers;
	@Autowired DailyPenaltyRepository penalties;
	@Autowired NotificationRepository notifications;
	@Autowired ProfileRepository profiles;
	@Autowired RankingRepository rankings;
	@Autowired FriendService friendService;
	@Autowired NotificationService notificationService;
	@Autowired PasswordEncoder encoder;
	@Autowired JwtTokenProvider tokens;
	private User first; private User second; private Character secondCharacter; private String firstToken; private String secondToken;

	@BeforeEach void setUp(){ clean(); first=createUser("social1@example.com","에덴");second=createUser("social2@example.com","루나");createCharacterAndWorld(first,"에덴");secondCharacter=createCharacterAndWorld(second,"루나");firstToken=tokens.generateAccessToken(first);secondToken=tokens.generateAccessToken(second); }
	@AfterEach void tearDown(){clean();}

	@Test void friendRequestAcceptListAndDeleteWork() throws Exception {
		mockMvc.perform(post("/api/friends").header("Authorization",bearer(firstToken)).contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"루나\"}"))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
		Friend request=friends.findAll().getFirst();
		mockMvc.perform(put("/api/friends/{id}/accept",request.getId()).header("Authorization",bearer(secondToken))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));
		mockMvc.perform(get("/api/friends").header("Authorization",bearer(firstToken))).andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(1))).andExpect(jsonPath("$[0].nickname").value("루나"));
		mockMvc.perform(delete("/api/friends/{id}",request.getId()).header("Authorization",bearer(firstToken))).andExpect(status().isNoContent());
		assertEquals(0,friends.count());
	}

	@Test void duplicateAndSelfFriendRequestsFail() throws Exception {
		friendService.request(first.getId(),new FriendRequestDTO(null,"루나"));
		mockMvc.perform(post("/api/friends").header("Authorization",bearer(firstToken)).contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"루나\"}")) .andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/friends").header("Authorization",bearer(firstToken)).contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"에덴\"}")) .andExpect(status().isBadRequest());
	}

	@Test void friendIslandVisitIsRecorded() throws Exception {
		acceptFriendship();
		mockMvc.perform(post("/api/visits/{id}",second.getId()).header("Authorization",bearer(firstToken))).andExpect(status().isOk()).andExpect(jsonPath("$.ownerNickname").value("루나"));
		assertEquals(1,visits.count());
		mockMvc.perform(get("/api/visits/history").header("Authorization",bearer(firstToken))).andExpect(jsonPath("$",hasSize(1)));
	}

	@Test void cheerAddsExperienceAndCannotRepeatToday() throws Exception {
		acceptFriendship();int before=secondCharacter.getExp();
		mockMvc.perform(post("/api/cheers/{id}",second.getId()).header("Authorization",bearer(firstToken))).andExpect(status().isOk()).andExpect(jsonPath("$.experienceReward").value(5));
		assertEquals(before+5,characters.findById(secondCharacter.getId()).orElseThrow().getExp());
		assertTrue(notifications.findByUserOrderByCreatedAtDesc(second).stream().anyMatch(n->n.getType()==NotificationType.CHEER_RECEIVED));
		mockMvc.perform(post("/api/cheers/{id}",second.getId()).header("Authorization",bearer(firstToken))).andExpect(status().isBadRequest());
	}

	@Test void missedDaysReturnVisualPenaltyWithoutDeletingGameData() throws Exception {
		first.recordLogin(LocalDateTime.now().minusDays(3));users.save(first);long worldCount=worlds.count();
		mockMvc.perform(get("/api/penalties/me").header("Authorization",bearer(firstToken))).andExpect(status().isOk()).andExpect(jsonPath("$.missedDays").value(3)).andExpect(jsonPath("$.stage").value("WILTED_FLOWERS"));
		assertEquals(worldCount,worlds.count());
	}

	@Test void notificationsCanBeCreatedListedAndRead() throws Exception {
		notificationService.create(first,NotificationType.SEASON_CHANGE,"계절이 바뀌었습니다.");notificationService.createDailyPrompts();
		mockMvc.perform(get("/api/notifications").header("Authorization",bearer(firstToken))).andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(2)));
		Long id=notifications.findByUserOrderByCreatedAtDesc(first).getFirst().getId();
		mockMvc.perform(put("/api/notifications/{id}/read",id).header("Authorization",bearer(firstToken))).andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true));
	}

	@Test void profileCanBeViewedAndUpdated() throws Exception {
		mockMvc.perform(put("/api/profiles/me").header("Authorization",bearer(firstToken)).contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"새에덴\",\"avatarUrl\":\"/avatar.png\",\"representativeIsland\":\"에덴섬\"}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("새에덴")).andExpect(jsonPath("$.avatarUrl").value("/avatar.png"));
		mockMvc.perform(get("/api/profiles/me").header("Authorization",bearer(firstToken))).andExpect(status().isOk()).andExpect(jsonPath("$.representativeIsland").value("에덴섬"));
	}

	@Test void partialProfileUpdateKeepsExistingValues() throws Exception {
		mockMvc.perform(put("/api/profiles/me").header("Authorization",bearer(firstToken)).contentType(MediaType.APPLICATION_JSON).content("{\"avatarUrl\":\"/avatar.png\",\"representativeIsland\":\"에덴섬\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(put("/api/profiles/me").header("Authorization",bearer(firstToken)).contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"새에덴\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").value("/avatar.png")).andExpect(jsonPath("$.representativeIsland").value("에덴섬"));
	}

	@Test void duplicateNicknameIsRejected() throws Exception {
		mockMvc.perform(put("/api/profiles/me").header("Authorization",bearer(firstToken)).contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"루나\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("이미 사용 중인 닉네임입니다."));
	}

	@Test void dailyPromptCreationIsIdempotentPerDay() {
		notificationService.createDailyPrompts();
		notificationService.createDailyPrompts();
		long dailyPromptCount = notifications.findByUserOrderByCreatedAtDesc(first).stream()
				.filter(notification -> notification.getType() == NotificationType.DAILY_PROMPT)
				.count();
		assertEquals(1, dailyPromptCount);
	}

	@Test void nonFriendCannotVisitOrCheer() throws Exception {
		mockMvc.perform(post("/api/visits/{id}",second.getId()).header("Authorization",bearer(firstToken)))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/cheers/{id}",second.getId()).header("Authorization",bearer(firstToken)))
				.andExpect(status().isBadRequest());
	}

	@Test void friendRankingIsSortedByMetrics() throws Exception {
		acceptFriendship();Ranking a=Ranking.create(users.findById(first.getId()).orElseThrow());a.updateMetrics(2,10,1);rankings.save(a);Ranking b=Ranking.create(users.findById(second.getId()).orElseThrow());b.updateMetrics(5,8,1);rankings.save(b);
		mockMvc.perform(get("/api/ranking/friends").header("Authorization",bearer(firstToken))).andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(2))).andExpect(jsonPath("$[0].nickname").value("루나"));
	}

	@Test void socialApisRequireJwt() throws Exception { mockMvc.perform(get("/api/friends")).andExpect(status().isUnauthorized()); }

	private void acceptFriendship(){var r=friendService.request(first.getId(),new FriendRequestDTO(null,"루나"));friendService.accept(second.getId(),r.friendshipId());}
	private User createUser(String email,String nickname){return users.save(new User(email,encoder.encode("password123"),nickname));}
	private Character createCharacterAndWorld(User u,String name){Character c=characters.save(Character.create(u,name,CharacterGender.NONE,HairStyle.PIXEL_CUT,"brown",Outfit.ROBE,CharacterJob.WIZARD));worlds.save(World.create(c,c.getId()));return c;}
	private String bearer(String token){return "Bearer "+token;}
	private void clean(){visits.deleteAll();cheers.deleteAll();notifications.deleteAll();friends.deleteAll();penalties.deleteAll();profiles.deleteAll();rankings.deleteAll();worlds.deleteAll();characters.deleteAll();users.deleteAll();}
}
