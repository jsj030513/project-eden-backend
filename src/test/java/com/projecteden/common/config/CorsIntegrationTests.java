package com.projecteden.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
		"app.cors.allowed-origins=http://localhost:5173,http://127.0.0.1:5173,http://mobile.test:5173"
})
class CorsIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void loginPreflightAllowsConfiguredOrigin() throws Exception {
		mockMvc.perform(options("/api/auth/login")
				.header(HttpHeaders.ORIGIN, "http://mobile.test:5173")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string(
						HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
						"http://mobile.test:5173"))
				.andExpect(header().string(
						HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
						org.hamcrest.Matchers.containsString("POST")))
				.andExpect(header().string(
						HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
						org.hamcrest.Matchers.containsString("content-type")));
	}

	@Test
	void signupPreflightAllowsConfiguredOrigin() throws Exception {
		mockMvc.perform(options("/api/users/signup")
				.header(HttpHeaders.ORIGIN, "http://localhost:5173")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string(
						HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
						"http://localhost:5173"));
	}

	@Test
	void preflightRejectsUnconfiguredOrigin() throws Exception {
		mockMvc.perform(options("/api/auth/login")
				.header(HttpHeaders.ORIGIN, "http://malicious.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
				.andExpect(status().isForbidden());
	}

	@Test
	void protectedApiStillRequiresJwt() throws Exception {
		mockMvc.perform(get("/api/village/interpretation"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void signupApiRemainsPublic() throws Exception {
		mockMvc.perform(post("/api/users/signup")
				.contentType("application/json")
				.content("""
						{
						  "email": "cors-signup@example.com",
						  "password": "password123",
						  "nickname": "cors-user"
						}
						"""))
				.andExpect(status().isCreated());
	}
}
