package com.projecteden.memorytaxonomy.observation.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

class RestClientOpenAIObservationClientTests {

	@Test
	void postsResponsesApiRequestAndParsesStructuredOutput() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://example.test/v1");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestClientOpenAIObservationClient client = client(builder.build(), 0);
		server.expect(once(), requestTo("https://example.test/v1/responses"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer test-key"))
				.andRespond(withSuccess(responseBody("""
						{
						  "recognized": true,
						  "confidence": 0.91,
						  "subjects": ["DOG"],
						  "objects": ["LEASH"],
						  "scene": "PARK",
						  "activities": ["WALKING"],
						  "relationships": [],
						  "moodSignals": ["WARM"]
						}
						"""), MediaType.APPLICATION_JSON));

		OpenAIObservationResponse response = client.observe(request());

		assertThat(response.recognized()).isTrue();
		assertThat(response.confidence()).isEqualByComparingTo("0.91");
		assertThat(response.subjects()).containsExactly("DOG");
		assertThat(response.objects()).containsExactly("LEASH");
		assertThat(response.scene()).isEqualTo("PARK");
		assertThat(response.activities()).containsExactly("WALKING");
		assertThat(response.moodSignals()).containsExactly("WARM");
		server.verify();
	}

	@Test
	void retriesOnceFor429AndThenSucceeds() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://example.test/v1");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestClientOpenAIObservationClient client = client(builder.build(), 1);
		server.expect(once(), requestTo("https://example.test/v1/responses"))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		server.expect(once(), requestTo("https://example.test/v1/responses"))
				.andRespond(withSuccess(responseBody("""
						{
						  "recognized": false,
						  "confidence": 0.2,
						  "subjects": [],
						  "objects": [],
						  "scene": null,
						  "activities": [],
						  "relationships": [],
						  "moodSignals": []
						}
						"""), MediaType.APPLICATION_JSON));

		OpenAIObservationResponse response = client.observe(request());

		assertThat(response.recognized()).isFalse();
		server.verify();
	}

	@Test
	void doesNotRetry400() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://example.test/v1");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestClientOpenAIObservationClient client = client(builder.build(), 1);
		server.expect(once(), requestTo("https://example.test/v1/responses"))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST));

		assertThatThrownBy(() -> client.observe(request()))
				.isInstanceOf(OpenAIObservationException.class)
				.hasMessageContaining("HTTP status 400");
		server.verify();
	}

	@Test
	void converts5xxAndInvalidJsonToSafeException() {
		RestClient.Builder serverErrorBuilder = RestClient.builder().baseUrl("https://example.test/v1");
		MockRestServiceServer serverError = MockRestServiceServer.bindTo(serverErrorBuilder).build();
		RestClientOpenAIObservationClient serverErrorClient = client(serverErrorBuilder.build(), 0);
		serverError.expect(once(), requestTo("https://example.test/v1/responses"))
				.andRespond(withServerError());

		assertThatThrownBy(() -> serverErrorClient.observe(request()))
				.isInstanceOf(OpenAIObservationException.class)
				.hasMessageContaining("HTTP status 500");
		serverError.verify();

		RestClient.Builder invalidJsonBuilder = RestClient.builder().baseUrl("https://example.test/v1");
		MockRestServiceServer invalidJson = MockRestServiceServer.bindTo(invalidJsonBuilder).build();
		RestClientOpenAIObservationClient invalidJsonClient = client(invalidJsonBuilder.build(), 0);
		invalidJson.expect(once(), requestTo("https://example.test/v1/responses"))
				.andRespond(withSuccess("{\"output_text\":\"not-json\"}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> invalidJsonClient.observe(request()))
				.isInstanceOf(OpenAIObservationException.class)
				.hasMessageContaining("could not be parsed");
		invalidJson.verify();
	}

	@Test
	void convertsTimeoutToSafeExceptionWithoutRetryWhenRetryDisabled() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://example.test/v1");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestClientOpenAIObservationClient client = client(builder.build(), 0);
		server.expect(once(), requestTo("https://example.test/v1/responses"))
				.andRespond(withException(new SocketTimeoutException("timed out")));

		assertThatThrownBy(() -> client.observe(request()))
				.isInstanceOf(OpenAIObservationException.class)
				.hasMessageContaining("timed out or was unreachable");
		server.verify();
	}

	private RestClientOpenAIObservationClient client(RestClient restClient, int maxRetries) {
		OpenAIObservationProperties properties = new OpenAIObservationProperties();
		properties.getOpenai().setApiKey("test-key");
		properties.getOpenai().setModel("test-model");
		properties.getOpenai().setBaseUrl("https://example.test/v1");
		properties.getOpenai().setMaxRetries(maxRetries);
		return new RestClientOpenAIObservationClient(properties, new ObjectMapper(), restClient);
	}

	private OpenAIObservationRequest request() {
		return new OpenAIObservationRequest(
				"test-model",
				"data:image/jpeg;base64,ZmFrZQ==",
				"low");
	}

	private String responseBody(String outputText) {
		try {
			return new ObjectMapper().writeValueAsString(java.util.Map.of("output_text", outputText));
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}
}
