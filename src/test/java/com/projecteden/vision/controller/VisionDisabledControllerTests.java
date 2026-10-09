package com.projecteden.vision.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.projecteden.auth.jwt.JwtAuthenticationFilter;
import com.projecteden.auth.jwt.JwtTokenProvider;
import com.projecteden.common.config.SecurityConfig;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.vision.client.EdenVisionClient;
import com.projecteden.vision.config.EdenVisionClientConfig;
import com.projecteden.vision.service.VisionService;

@WebMvcTest(controllers = VisionController.class, properties = "eden.vision-api.enabled=false")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
		VisionService.class, EdenVisionClientConfig.class})
@ActiveProfiles("test")
class VisionDisabledControllerTests {
	@Autowired private MockMvc mockMvc;
	@Autowired private JwtTokenProvider jwtTokenProvider;
	@Autowired private ApplicationContext context;
	@MockitoBean private UserRepository users;

	@Test
	void startsWithoutClientAndReturnsServiceUnavailableToAuthenticatedUser() throws Exception {
		assertThat(context.getBeansOfType(EdenVisionClient.class)).isEmpty();
		User user = new User("disabled-vision@example.com", "unused", "disabled-vision");
		ReflectionTestUtils.setField(user, "id", 43L);
		when(users.findById(43L)).thenReturn(Optional.of(user));
		mockMvc.perform(multipart("/api/vision/classify")
				.file(new MockMultipartFile("image", "photo.png", "image/png", new byte[] {1}))
				.header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(user)))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("VISION_DISABLED"))
				.andExpect(jsonPath("$.message").value("Vision analysis is disabled."));
	}

	@Test
	void disabledEndpointStillRequiresJwt() throws Exception {
		mockMvc.perform(multipart("/api/vision/classify"))
				.andExpect(status().isUnauthorized());
	}
}
