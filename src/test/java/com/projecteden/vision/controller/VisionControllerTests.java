package com.projecteden.vision.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecteden.auth.jwt.JwtAuthenticationFilter;
import com.projecteden.auth.jwt.JwtTokenProvider;
import com.projecteden.common.config.SecurityConfig;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;
import com.projecteden.vision.client.EdenVisionClient;
import com.projecteden.vision.dto.VisionClassifyResponse;
import com.projecteden.vision.exception.EdenVisionUpstreamException;
import com.projecteden.vision.exception.EdenVisionUpstreamException.Kind;
import com.projecteden.vision.service.VisionService;

@WebMvcTest(controllers = VisionController.class, properties = "eden.vision-api.enabled=true")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class, VisionService.class})
@ActiveProfiles("test")
class VisionControllerTests {
	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private JwtTokenProvider jwtTokenProvider;
	@MockitoBean private UserRepository users;
	@MockitoBean private EdenVisionClient client;

	private String authorization;
	private String fixture;
	private VisionClassifyResponse response;

	@BeforeEach
	void setUp() throws Exception {
		User user = new User("vision-test@example.com", "unused", "vision-test");
		ReflectionTestUtils.setField(user, "id", 42L);
		when(users.findById(42L)).thenReturn(Optional.of(user));
		authorization = "Bearer " + jwtTokenProvider.generateAccessToken(user);
		fixture = new ClassPathResource("vision/ranking-1.1.json").getContentAsString(StandardCharsets.UTF_8);
		response = objectMapper.readValue(fixture, VisionClassifyResponse.class);
	}

	@Test
	void requiresJwt() throws Exception {
		mockMvc.perform(multipart("/api/vision/classify").file(image()))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(client);
	}

	@Test
	void rejectsInvalidJwt() throws Exception {
		mockMvc.perform(multipart("/api/vision/classify").file(image()).header("Authorization", "Bearer invalid"))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(client);
	}

	@Test
	void requiresImagePart() throws Exception {
		mockMvc.perform(multipart("/api/vision/classify").header("Authorization", authorization))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VISION_IMAGE_REQUIRED"));
		verifyNoInteractions(client);
	}

	@Test
	void doesNotAcceptPhotoFileFieldInPlaceOfImage() throws Exception {
		mockMvc.perform(multipart("/api/vision/classify")
				.file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] {1}))
				.header("Authorization", authorization))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VISION_IMAGE_REQUIRED"));
		verifyNoInteractions(client);
	}

	@Test
	void rejectsEmptyImage() throws Exception {
		mockMvc.perform(multipart("/api/vision/classify")
				.file(new MockMultipartFile("image", "empty.png", "image/png", new byte[0]))
				.header("Authorization", authorization))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VISION_IMAGE_EMPTY"));
		verifyNoInteractions(client);
	}

	@Test
	void allowsExactlyTenMiBAndDelegatesDecoding() throws Exception {
		MockMultipartFile file = new MockMultipartFile("image", "image.bin", "application/octet-stream",
				new byte[10 * 1024 * 1024]);
		when(client.classify(file)).thenReturn(response);
		mockMvc.perform(multipart("/api/vision/classify").file(file).header("Authorization", authorization))
				.andExpect(status().isOk());
		verify(client).classify(file);
		verifyNoMoreInteractions(client);
	}

	@Test
	void rejectsTenMiBPlusOneByteBeforeCallingClient() throws Exception {
		mockMvc.perform(multipart("/api/vision/classify")
				.file(new MockMultipartFile("image", "large.jpg", "image/jpeg", new byte[10 * 1024 * 1024 + 1]))
				.header("Authorization", authorization))
				.andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("VISION_IMAGE_TOO_LARGE"));
		verifyNoInteractions(client);
	}

	@ParameterizedTest
	@CsvSource({"png,image/png", "jpg,image/jpeg"})
	void returnsEntireRankingResponseUnchangedForPngAndJpeg(String format, String mime) throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		assertThat(ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, bytes)).isTrue();
		MockMultipartFile file = new MockMultipartFile("image", "photo." + format, mime, bytes.toByteArray());
		when(client.classify(file)).thenReturn(response);
		var result = mockMvc.perform(multipart("/api/vision/classify").file(file).header("Authorization", authorization))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.schema_version").value("1.1"))
				.andExpect(jsonPath("$.predictions").isEmpty())
				.andExpect(jsonPath("$.unknown_decision").value("not_applied"))
				.andExpect(jsonPath("$.ranking[0].raw_score").value(27.1))
				.andExpect(jsonPath("$.ranking[4].raw_score").value(-3.0))
				.andExpect(jsonPath("$.ranking[0].confidence").doesNotExist())
				.andReturn();
		// Exact JSON comparison includes request_id, metadata, nullable fields and ranking order.
		assertThat(objectMapper.readTree(result.getResponse().getContentAsString())).isEqualTo(objectMapper.readTree(fixture));
		verify(client).classify(file);
		verifyNoMoreInteractions(client);
	}

	static Stream<Arguments> upstreamErrors() {
		return Stream.of(
				Arguments.of(Kind.CONNECTION_FAILURE, null, 503, "VISION_CONNECTION_FAILED"),
				Arguments.of(Kind.TIMEOUT, null, 504, "VISION_TIMEOUT"),
				Arguments.of(Kind.MALFORMED_RESPONSE, null, 502, "VISION_INVALID_RESPONSE"),
				Arguments.of(Kind.HTTP_ERROR, 500, 502, "VISION_UPSTREAM_ERROR"),
				Arguments.of(Kind.HTTP_ERROR, 503, 502, "VISION_UPSTREAM_ERROR"),
				Arguments.of(Kind.HTTP_ERROR, 400, 400, "VISION_INVALID_REQUEST"),
				Arguments.of(Kind.HTTP_ERROR, 413, 413, "VISION_IMAGE_TOO_LARGE"),
				Arguments.of(Kind.HTTP_ERROR, 415, 415, "VISION_IMAGE_UNSUPPORTED"),
				Arguments.of(Kind.HTTP_ERROR, 422, 422, "VISION_IMAGE_INVALID"),
				Arguments.of(Kind.HTTP_ERROR, 401, 502, "VISION_UPSTREAM_ERROR"),
				Arguments.of(Kind.HTTP_ERROR, 403, 502, "VISION_UPSTREAM_ERROR"),
				Arguments.of(Kind.HTTP_ERROR, 404, 502, "VISION_UPSTREAM_ERROR"),
				Arguments.of(Kind.HTTP_ERROR, 429, 502, "VISION_UPSTREAM_ERROR"),
				Arguments.of(Kind.HTTP_ERROR, null, 502, "VISION_UPSTREAM_ERROR"));
	}

	@ParameterizedTest
	@MethodSource("upstreamErrors")
	void mapsUpstreamErrors(Kind kind, Integer upstreamStatus, int expectedStatus, String code) throws Exception {
		when(client.classify(any(MultipartFile.class))).thenThrow(new EdenVisionUpstreamException(kind, upstreamStatus));
		var result = mockMvc.perform(multipart("/api/vision/classify").file(image()).header("Authorization", authorization))
				.andExpect(status().is(expectedStatus)).andExpect(jsonPath("$.code").value(code))
				.andExpect(jsonPath("$.message").isString()).andReturn();
		assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).size()).isEqualTo(2);
		verify(client).classify(any(MultipartFile.class));
		verifyNoMoreInteractions(client);
	}

	@Test
	void sanitizesInvalidMultipartMetadataErrors() throws Exception {
		when(client.classify(any(MultipartFile.class))).thenThrow(new IllegalArgumentException("SECRET JWT /private/image-path"));
		var result = mockMvc.perform(multipart("/api/vision/classify").file(image()).header("Authorization", authorization))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VISION_INVALID_REQUEST"))
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain("SECRET", "JWT", "/private", "stacktrace");
	}

	private MockMultipartFile image() {
		return new MockMultipartFile("image", "sample.png", "image/png", new byte[] {1, 2, 3});
	}
}
