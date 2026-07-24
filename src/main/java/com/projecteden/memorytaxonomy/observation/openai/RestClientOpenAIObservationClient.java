package com.projecteden.memorytaxonomy.observation.openai;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class RestClientOpenAIObservationClient implements OpenAIObservationClient {

	private static final Logger log = LoggerFactory.getLogger(RestClientOpenAIObservationClient.class);
	private static final String OBSERVATION_PROMPT = """
			You observe an image for Project Eden.
			Return only visible and reasonably supported facts.
			Do not choose Project Eden categories, rewards, themes, village changes,
			NPC dialogue, or game outcomes.
			Do not identify people.
			Do not infer sensitive personal traits.
			Do not guess relationships, moods, activities, or places without visible evidence.
			Return only valid JSON matching the required schema.
			Use uppercase stable codes.
			Use empty arrays or null when uncertain.
			""";

	private final OpenAIObservationProperties properties;
	private final ObjectMapper objectMapper;
	private final RestClient.Builder restClientBuilder;
	private final RestClient fixedRestClient;

	@Autowired
	public RestClientOpenAIObservationClient(
			OpenAIObservationProperties properties,
			ObjectMapper objectMapper,
			RestClient.Builder restClientBuilder) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.restClientBuilder = restClientBuilder;
		this.fixedRestClient = null;
	}

	RestClientOpenAIObservationClient(
			OpenAIObservationProperties properties,
			ObjectMapper objectMapper,
			RestClient fixedRestClient) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.restClientBuilder = null;
		this.fixedRestClient = fixedRestClient;
	}

	@Override
	public OpenAIObservationResponse observe(OpenAIObservationRequest request) {
		RuntimeException lastFailure = null;
		int attempts = Math.max(0, properties.getOpenai().getMaxRetries()) + 1;
		for (int attempt = 1; attempt <= attempts; attempt++) {
			try {
				return execute(request);
			} catch (HttpStatusCodeException ex) {
				lastFailure = ex;
				if (!shouldRetry(ex.getStatusCode()) || attempt == attempts) {
					throw new OpenAIObservationException(
							"OpenAI observation request failed with HTTP status " + ex.getStatusCode().value(), ex);
				}
				log.warn("OpenAI observation transient failure status={} attempt={}/{}",
						ex.getStatusCode().value(), attempt, attempts);
			} catch (ResourceAccessException ex) {
				lastFailure = ex;
				if (attempt == attempts) {
					throw new OpenAIObservationException("OpenAI observation request timed out or was unreachable", ex);
				}
				log.warn("OpenAI observation transient I/O failure attempt={}/{}", attempt, attempts);
			} catch (OpenAIObservationException ex) {
				throw ex;
			}
			backoff();
		}
		throw new OpenAIObservationException("OpenAI observation request failed", lastFailure);
	}

	private OpenAIObservationResponse execute(OpenAIObservationRequest request) {
		long startedAt = System.currentTimeMillis();
		String responseBody = restClient()
				.post()
				.uri("/responses")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + properties.getOpenai().getApiKey())
				.body(requestBody(request))
				.retrieve()
				.body(String.class);
		long latencyMs = System.currentTimeMillis() - startedAt;
		if (responseBody == null || responseBody.isBlank()) {
			throw new OpenAIObservationException("OpenAI observation response was empty");
		}
		OpenAIObservationResponse response = parseResponse(responseBody);
		log.debug("OpenAI observation response received provider=openai model={} latencyMs={} recognized={} signalCounts={}/{}/{}/{}/{}",
				request.model(),
				latencyMs,
				response.recognized(),
				size(response.subjects()),
				size(response.objects()),
				response.scene() == null ? 0 : 1,
				size(response.activities()),
				size(response.relationships()));
		return response;
	}

	private RestClient restClient() {
		if (fixedRestClient != null) {
			return fixedRestClient;
		}
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofMillis(properties.getOpenai().getConnectTimeoutMs()));
		requestFactory.setReadTimeout(Duration.ofMillis(properties.getOpenai().getReadTimeoutMs()));
		return restClientBuilder
				.baseUrl(trimTrailingSlash(properties.getOpenai().getBaseUrl()))
				.requestFactory(requestFactory)
				.build();
	}

	private Map<String, Object> requestBody(OpenAIObservationRequest request) {
		return Map.of(
				"model", request.model(),
				"input", List.of(Map.of(
						"role", "user",
						"content", List.of(
								Map.of("type", "input_text", "text", OBSERVATION_PROMPT),
								Map.of(
										"type", "input_image",
										"image_url", request.imageDataUrl(),
										"detail", request.imageDetail())))),
				"text", Map.of("format", responseFormat()));
	}

	private Map<String, Object> responseFormat() {
		return Map.of(
				"type", "json_schema",
				"name", "eden_image_observation",
				"strict", true,
				"schema", Map.of(
						"type", "object",
						"additionalProperties", false,
						"required", List.of(
								"recognized",
								"confidence",
								"subjects",
								"objects",
								"scene",
								"activities",
								"relationships",
								"moodSignals"),
						"properties", Map.of(
								"recognized", Map.of("type", "boolean"),
								"confidence", Map.of("type", "number", "minimum", 0, "maximum", 1),
								"subjects", stringArraySchema(),
								"objects", stringArraySchema(),
								"scene", Map.of("type", List.of("string", "null")),
								"activities", stringArraySchema(),
								"relationships", stringArraySchema(),
								"moodSignals", stringArraySchema())));
	}

	private Map<String, Object> stringArraySchema() {
		return Map.of(
				"type", "array",
				"items", Map.of("type", "string"),
				"maxItems", 20);
	}

	private OpenAIObservationResponse parseResponse(String responseBody) {
		try {
			JsonNode root = objectMapper.readTree(responseBody);
			String outputText = outputText(root);
			if (outputText == null || outputText.isBlank()) {
				throw new OpenAIObservationException("OpenAI observation output text was empty");
			}
			return objectMapper.readValue(outputText, OpenAIObservationResponse.class);
		} catch (OpenAIObservationException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new OpenAIObservationException("OpenAI observation response could not be parsed", ex);
		}
	}

	private String outputText(JsonNode root) {
		JsonNode direct = root.get("output_text");
		if (direct != null && direct.isTextual()) {
			return direct.asText();
		}
		JsonNode output = root.get("output");
		if (output == null || !output.isArray()) {
			return null;
		}
		for (JsonNode outputItem : output) {
			JsonNode content = outputItem.get("content");
			if (content == null || !content.isArray()) {
				continue;
			}
			for (JsonNode contentItem : content) {
				JsonNode text = contentItem.get("text");
				if (text != null && text.isTextual()) {
					return text.asText();
				}
			}
		}
		return null;
	}

	private boolean shouldRetry(HttpStatusCode statusCode) {
		return statusCode.value() == 429 || statusCode.is5xxServerError();
	}

	private void backoff() {
		try {
			Thread.sleep(100);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private String trimTrailingSlash(String baseUrl) {
		if (baseUrl == null || baseUrl.isBlank()) {
			return "https://api.openai.com/v1";
		}
		return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	private int size(List<String> values) {
		return values == null ? 0 : values.size();
	}
}
