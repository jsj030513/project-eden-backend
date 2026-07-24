package com.projecteden.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecteden.auth.jwt.JwtTokenProvider;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private ObjectMapper objectMapper;

	private User user;

	@BeforeEach
	void setUp() {
		userRepository.deleteAll();
		user = userRepository.save(new User(
				"test@example.com",
				passwordEncoder.encode("password123"),
				"eden"));
	}

	@Test
	void loginSucceedsAndReturnsAccessToken() throws Exception {
		MvcResult result = performLogin("test@example.com", "password123")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.userId").value(user.getId()))
				.andExpect(jsonPath("$.email").value("test@example.com"))
				.andExpect(jsonPath("$.nickname").value("eden"))
				.andReturn();

		String accessToken = readAccessToken(result);
		assertTrue(jwtTokenProvider.validateToken(accessToken));
		assertEquals(user.getId(), jwtTokenProvider.getUserId(accessToken));
	}

	@Test
	void loginNormalizesEmailAndAcceptsLegacyBcryptHash() throws Exception {
		assertTrue(user.getPassword().startsWith("$2a$"));

		performLogin(" TEST@EXAMPLE.COM ", "password123")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty());
	}

	@Test
	void loginFailsWithWrongPassword() throws Exception {
		performLogin("test@example.com", "wrong-password")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message")
						.value("이메일 또는 비밀번호가 올바르지 않습니다."));
	}

	@Test
	void loginFailsWithUnknownEmail() throws Exception {
		performLogin("unknown@example.com", "password123")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message")
						.value("이메일 또는 비밀번호가 올바르지 않습니다."));
	}

	@Test
	void meSucceedsWithAccessToken() throws Exception {
		MvcResult loginResult = performLogin("test@example.com", "password123")
				.andExpect(status().isOk())
				.andReturn();
		String accessToken = readAccessToken(loginResult);

		mockMvc.perform(get("/api/users/me")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(user.getId()))
				.andExpect(jsonPath("$.email").value("test@example.com"))
				.andExpect(jsonPath("$.nickname").value("eden"));
	}

	@Test
	void meFailsWithoutAccessToken() throws Exception {
		mockMvc.perform(get("/api/users/me"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void meFailsWithMalformedTokenOrMissingBearerPrefix() throws Exception {
		mockMvc.perform(get("/api/users/me")
				.header("Authorization", "Bearer not-a-jwt"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/users/me")
				.header("Authorization", jwtTokenProvider.generateAccessToken(user)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void loginRejectsBlankRequestFields() throws Exception {
		performLogin("", "")
				.andExpect(status().isBadRequest());
	}

	@Test
	void inactiveUserCannotLoginOrUsePreviouslyIssuedToken() throws Exception {
		MvcResult loginResult = performLogin("test@example.com", "password123")
				.andExpect(status().isOk())
				.andReturn();
		String accessToken = readAccessToken(loginResult);

		user.changeStatus("INACTIVE");
		userRepository.saveAndFlush(user);

		performLogin("test@example.com", "password123")
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/users/me")
				.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isUnauthorized());
	}

	private org.springframework.test.web.servlet.ResultActions performLogin(
			String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "%s",
						  "password": "%s"
						}
						""".formatted(email, password)));
	}

	private String readAccessToken(MvcResult result) throws Exception {
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
		return response.get("accessToken").asText();
	}
}
