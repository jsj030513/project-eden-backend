package com.projecteden.vision.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecteden.vision.exception.EdenVisionUpstreamException;
import com.projecteden.vision.exception.EdenVisionUpstreamException.Kind;

class EdenVisionClientTests {
	private MockRestServiceServer server;
	private EdenVisionClient client;
	private String fixture;
	private final MockMultipartFile image = new MockMultipartFile("browser-field", "sample.png", "image/png",
			new byte[] { (byte) 0x89, 0x50, 0x4e, 0x47, 0, 1, 2, (byte) 0xff });

	@BeforeEach
	void setUp() throws IOException {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://vision.example");
		server = MockRestServiceServer.bindTo(builder).build();
		client = new EdenVisionClient(builder.build(), new ObjectMapper());
		fixture = new ClassPathResource("vision/ranking-1.1.json").getContentAsString(StandardCharsets.UTF_8);
	}

	@Test
	void sendsMultipartAndPreservesRawRankingWithoutConfidenceConversion() throws Exception {
		server.expect(requestTo("https://vision.example/v1/vision/classify"))
				.andExpect(method(HttpMethod.POST)).andExpect(request -> {
					assertThat(request.getHeaders().getContentType().isCompatibleWith(MediaType.MULTIPART_FORM_DATA)).isTrue();
					String boundary = request.getHeaders().getContentType().getParameter("boundary");
					assertThat(boundary).isNotBlank();
					byte[] body = ((MockClientHttpRequest) request).getBodyAsBytes();
					String wire = new String(body, StandardCharsets.ISO_8859_1);
					assertThat(wire).contains("--" + boundary, "name=\"image\"; filename=\"sample.png\"",
							"Content-Type: image/png", new String(image.getBytes(), StandardCharsets.ISO_8859_1));
					assertThat(wire).doesNotContain("browser-field");
				}).andRespond(withSuccess(fixture, MediaType.APPLICATION_JSON));
		var result = client.classify(image);
		assertThat(result.schemaVersion()).isEqualTo("1.1");
		assertThat(result.status()).isEqualTo("ranked");
		assertThat(result.requestId()).isEqualTo("synthetic-ranking-fixture");
		assertThat(result.image().width()).isEqualTo(640);
		assertThat(result.meta().modelId()).isEqualTo("synthetic-ranking-fixture");
		assertThat(result.meta().calibrationVersion()).isNull();
		assertThat(result.predictions()).isEmpty();
		assertThat(result.unknownDecision()).isEqualTo("not_applied");
		assertThat(result.ranking()).extracting(item -> item.rawScore()).containsExactly(27.1, 25.2, 24.8, 1.2, -3.0);
		String json = new ObjectMapper().writeValueAsString(result);
		assertThat(json).contains("\"raw_score\":27.1", "\"raw_score\":-3.0", "\"score_kind\":\"raw_image_text_logit\"")
				.doesNotContain("confidence", "probability");
		server.verify();
	}

	@ParameterizedTest
	@ValueSource(ints = {302, 400, 413, 422, 500, 503})
	void abstractsNonSuccessWithoutRetainingBody(int status) {
		server.expect(requestTo("https://vision.example/v1/vision/classify"))
				.andRespond(withStatus(HttpStatusCode.valueOf(status)).body("SECRET /private/path image bytes JWT"));
		var exception = catchThrowableOfType(() -> client.classify(image), EdenVisionUpstreamException.class);
		assertThat(exception.getKind()).isEqualTo(Kind.HTTP_ERROR);
		assertThat(exception.getStatusCode()).isEqualTo(status);
		assertThat(exception.getMessage()).doesNotContain("SECRET", "/private/path", "JWT");
		assertThat(exception.getCause()).isNull();
		server.verify();
	}

	@ParameterizedTest
	@ValueSource(strings = {"{broken SECRET", "", "null", "[]", "{}"})
	void abstractsMalformedOrEmptyResponse(String body) {
		server.expect(requestTo("https://vision.example/v1/vision/classify"))
				.andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
		var exception = catchThrowableOfType(() -> client.classify(image), EdenVisionUpstreamException.class);
		assertThat(exception.getKind()).isEqualTo(Kind.MALFORMED_RESPONSE);
		assertThat(exception.getMessage()).doesNotContain("SECRET");
		assertThat(exception.getCause()).isNull();
		server.verify();
	}

	@Test
	void distinguishesTimeout() {
		assertTransportFailure(new SocketTimeoutException("SECRET host"), Kind.TIMEOUT);
	}

	@Test
	void distinguishesConnectionFailure() {
		assertTransportFailure(new ConnectException("SECRET host"), Kind.CONNECTION_FAILURE);
	}

	@Test
	void distinguishesTimeoutWhileReadingResponseBody() {
		assertResponseReadFailure(new SocketTimeoutException("SECRET host"), Kind.TIMEOUT);
	}

	@Test
	void distinguishesConnectionLossWhileReadingResponseBody() {
		assertResponseReadFailure(new IOException("SECRET host"), Kind.CONNECTION_FAILURE);
	}

	private void assertResponseReadFailure(IOException failure, Kind kind) {
		server.expect(requestTo("https://vision.example/v1/vision/classify")).andRespond(request ->
				new MockClientHttpResponse(new InputStream() {
					@Override public int read() throws IOException { throw failure; }
				}, HttpStatus.OK));
		var exception = catchThrowableOfType(() -> client.classify(image), EdenVisionUpstreamException.class);
		assertThat(exception.getKind()).isEqualTo(kind);
		assertThat(exception.getMessage()).doesNotContain("SECRET", "host");
		assertThat(exception.getCause()).isNull();
		server.verify();
	}

	private void assertTransportFailure(IOException failure, Kind kind) {
		server.expect(requestTo("https://vision.example/v1/vision/classify")).andRespond(withException(failure));
		var exception = catchThrowableOfType(() -> client.classify(image), EdenVisionUpstreamException.class);
		assertThat(exception.getKind()).isEqualTo(kind);
		assertThat(exception.getMessage()).doesNotContain("SECRET", "host");
		assertThat(exception.getCause()).isNull();
		server.verify();
	}
}
