package com.projecteden.user;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserSignupIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void clearUsers() {
		userRepository.deleteAll();
	}

	@Test
	void signupSucceeds() throws Exception {
		mockMvc.perform(post("/api/users/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "test@example.com",
						  "password": "password123",
						  "nickname": "eden"
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.email").value("test@example.com"))
				.andExpect(jsonPath("$.nickname").value("eden"));

		User savedUser = userRepository.findByEmail("test@example.com").orElseThrow();
		assertFalse(savedUser.getPassword().equals("password123"));
		assertTrue(passwordEncoder.matches("password123", savedUser.getPassword()));
		assertEquals("LOCAL", savedUser.getProvider());
		assertEquals("USER", savedUser.getRole());
		assertEquals("ACTIVE", savedUser.getStatus());
		assertNotNull(savedUser.getCreatedAt());
		assertNotNull(savedUser.getUpdatedAt());
	}

	@Test
	void duplicateEmailFails() throws Exception {
		userRepository.save(new User("test@example.com", "encoded-password", "existing"));

		mockMvc.perform(post("/api/users/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "test@example.com",
						  "password": "password123",
						  "nickname": "eden"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."));
	}

	@Test
	void invalidEmailFailsValidation() throws Exception {
		mockMvc.perform(post("/api/users/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "invalid-email",
						  "password": "password123",
						  "nickname": "eden"
						}
						"""))
				.andExpect(status().isBadRequest());
	}
}
