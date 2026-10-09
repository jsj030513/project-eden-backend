package com.projecteden.vision.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.multipart.MultipartFile;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;

import com.projecteden.auth.jwt.JwtTokenProvider;
import com.projecteden.user.domain.User;
import com.projecteden.user.repository.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.datasource.url=jdbc:h2:mem:vision_multipart_http;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"eden.vision-api.enabled=true",
		"eden.vision-api.base-url=http://127.0.0.1:1"
})
@ActiveProfiles("test")
class VisionMultipartBoundaryHttpTests {
	private static final int MIB = 1024 * 1024;
	@LocalServerPort private int port;
	@org.springframework.beans.factory.annotation.Autowired private UserRepository users;
	@org.springframework.beans.factory.annotation.Autowired private JwtTokenProvider tokens;
	@MockitoSpyBean private com.projecteden.vision.service.VisionService visionService;
	@MockitoSpyBean private com.projecteden.vision.client.EdenVisionClient visionClient;
	private String token;
	private final HttpClient http = HttpClient.newHttpClient();

	@BeforeEach
	void createAuthenticatedUser() {
		User user = users.save(new User("vision-boundary-" + UUID.randomUUID() + "@example.test",
				"unused", "vision-boundary-" + UUID.randomUUID()));
		token = tokens.generateAccessToken(user);
		assertThat(tokens.validateToken(token)).isTrue();
		assertThat(tokens.getUserId(token)).isEqualTo(user.getId());
	}

	@Test
	void jwtAuthenticationWorksOverHttpWithoutSecurityWorkaround() throws Exception {
		ResponseEntity<String> valid = upload(1, 0, token);
		assertThat(valid.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(valid.getBody()).contains("VISION_CONNECTION_FAILED");

		ResponseEntity<String> missing = upload(1, 0, null);
		assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

		ResponseEntity<String> invalid = upload(1, 0, "not-a-valid-jwt");
		assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void acceptsVisionImagesThroughTenMiBAndMapsLargerServletRejection() throws Exception {
		for (int size : new int[] {MIB * 10 - 1, MIB * 10}) {
			ResponseEntity<String> response = upload(size, 0);
			assertThat(response.getStatusCode().value()).as("image bytes=%s", size).isEqualTo(503);
			assertThat(response.getBody()).contains("VISION_CONNECTION_FAILED");
		}

		clearInvocations(visionService, visionClient);
		ResponseEntity<String> over = upload(MIB * 10 + 1, 0);
		assertVisionTooLarge(over);
		verify(visionService).classify(any(MultipartFile.class));
		verifyNoInteractions(visionClient);

		clearInvocations(visionService, visionClient);
		ResponseEntity<String> overInfrastructureLimit = upload(MIB * 10 + 2, 0);
		assertVisionTooLarge(overInfrastructureLimit);
		verifyNoInteractions(visionService, visionClient);
	}

	private ResponseEntity<String> upload(int imageSize, int extraSize) throws Exception {
		return upload(imageSize, extraSize, token);
	}

	private ResponseEntity<String> upload(int imageSize, int extraSize, String bearerToken) throws Exception {
		String boundary = "eden-boundary-" + UUID.randomUUID();
		byte[] prefix = ("--" + boundary + "\r\n"
				+ "Content-Disposition: form-data; name=\"image\"; filename=\"image.jpg\"\r\n"
				+ "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
		byte[] image = new byte[imageSize];
		byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
		byte[] body = new byte[prefix.length + image.length + suffix.length];
		System.arraycopy(prefix, 0, body, 0, prefix.length);
		System.arraycopy(image, 0, body, prefix.length, image.length);
		System.arraycopy(suffix, 0, body, prefix.length + image.length, suffix.length);

		HttpRequest.Builder request = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + port + "/api/vision/classify"))
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body));
		if (bearerToken != null) request.header("Authorization", "Bearer " + bearerToken);
		HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
		return ResponseEntity.status(response.statusCode()).body(response.body());
	}

	private void assertVisionTooLarge(ResponseEntity<String> response) {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
		assertThat(response.getBody()).contains("VISION_IMAGE_TOO_LARGE")
				.doesNotContain("127.0.0.1", "Exception", "stacktrace");
	}
}
