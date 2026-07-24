package com.projecteden.memorytaxonomy.observation;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.projecteden.memorytaxonomy.observation.openai.OpenAIObservationClient;
import com.projecteden.memorytaxonomy.observation.openai.OpenAIObservationException;
import com.projecteden.memorytaxonomy.observation.openai.OpenAIObservationProperties;
import com.projecteden.memorytaxonomy.observation.openai.OpenAIObservationRequest;
import com.projecteden.memorytaxonomy.observation.openai.OpenAIObservationResponse;

@Component
public class OpenAIImageObservationProvider implements ImageObservationProvider {

	public static final String PROVIDER = "OPENAI";

	private static final Logger log = LoggerFactory.getLogger(OpenAIImageObservationProvider.class);
	private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9_]{1,40}");
	private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
			"image/jpeg",
			"image/png",
			"image/webp",
			"image/gif");
	private static final int MAX_SUBJECTS = 10;
	private static final int MAX_OBJECTS = 20;
	private static final int MAX_ACTIVITIES = 10;
	private static final int MAX_RELATIONSHIPS = 5;
	private static final int MAX_MOOD_SIGNALS = 5;

	private final OpenAIObservationProperties properties;
	private final OpenAIObservationClient client;
	private final MockImageObservationProvider mockProvider;

	public OpenAIImageObservationProvider(
			OpenAIObservationProperties properties,
			OpenAIObservationClient client,
			MockImageObservationProvider mockProvider) {
		this.properties = properties;
		this.client = client;
		this.mockProvider = mockProvider;
	}

	@Override
	public ImageObservation observe(ImageObservationRequest request) {
		if (!properties.isOpenAIConfigured()) {
			log.warn("OpenAI image observation is selected but api key or model is missing. Falling back to mock provider.");
			return mockProvider.observe(request);
		}
		if (!request.hasImageBytes()) {
			log.warn("OpenAI image observation skipped because image bytes are not available for photoId={}. Falling back to mock provider.",
					request.photoId());
			return mockProvider.observe(request);
		}
		if (request.fileSize() > properties.getMaxImageBytes()) {
			log.warn("OpenAI image observation skipped because fileSize={} exceeds maxImageBytes={}. Falling back to mock provider.",
					request.fileSize(),
					properties.getMaxImageBytes());
			return mockProvider.observe(request);
		}
		if (!isSupportedContentType(request.contentType())) {
			log.warn("OpenAI image observation skipped because contentType={} is unsupported. Falling back to mock provider.",
					request.contentType());
			return mockProvider.observe(request);
		}

		try {
			OpenAIObservationResponse response = client.observe(new OpenAIObservationRequest(
					properties.getOpenai().getModel(),
					dataUrl(request.contentType(), request.imageBytes()),
					imageDetail()));
			return toObservation(response);
		} catch (OpenAIObservationException ex) {
			log.warn("OpenAI image observation failed safely. Falling back to mock provider. reason={}",
					ex.getMessage());
			return mockProvider.observe(request);
		}
	}

	@Override
	public String provider() {
		return PROVIDER;
	}

	@Override
	public String modelVersion() {
		return properties.getOpenai().getModel();
	}

	private ImageObservation toObservation(OpenAIObservationResponse response) {
		if (response == null) {
			return ImageObservation.fallback(provider(), modelVersion());
		}
		BigDecimal confidence = validConfidence(response.confidence());
		List<String> subjects = normalize(response.subjects(), MAX_SUBJECTS);
		List<String> objects = normalize(response.objects(), MAX_OBJECTS);
		String scene = normalizeScene(response.scene());
		List<String> activities = normalize(response.activities(), MAX_ACTIVITIES);
		List<String> relationships = normalize(response.relationships(), MAX_RELATIONSHIPS);
		List<String> moodSignals = normalize(response.moodSignals(), MAX_MOOD_SIGNALS);

		boolean hasSignal = !subjects.isEmpty()
				|| !objects.isEmpty()
				|| scene != null
				|| !activities.isEmpty()
				|| !relationships.isEmpty()
				|| !moodSignals.isEmpty();
		if (!Boolean.TRUE.equals(response.recognized()) || !hasSignal) {
			return ImageObservation.fallback(provider(), modelVersion());
		}
		return ImageObservation.recognized(
				subjects,
				objects,
				scene,
				activities,
				relationships,
				moodSignals,
				provider(),
				modelVersion(),
				confidence);
	}

	private BigDecimal validConfidence(BigDecimal confidence) {
		if (confidence == null
				|| confidence.compareTo(BigDecimal.ZERO) < 0
				|| confidence.compareTo(BigDecimal.ONE) > 0) {
			throw new OpenAIObservationException("OpenAI observation confidence was invalid");
		}
		return confidence;
	}

	private List<String> normalize(List<String> values, int maxSize) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		LinkedHashSet<String> normalized = new LinkedHashSet<>();
		for (String value : values) {
			String code = normalizeCode(value);
			if (code != null) {
				normalized.add(code);
			}
			if (normalized.size() >= maxSize) {
				break;
			}
		}
		return List.copyOf(normalized);
	}

	private String normalizeScene(String scene) {
		return normalizeCode(scene);
	}

	private String normalizeCode(String value) {
		if (value == null) {
			return null;
		}
		String code = value.trim()
				.toUpperCase(Locale.ROOT)
				.replaceAll("[\\s-]+", "_")
				.replaceAll("[^A-Z0-9_]", "");
		if (code.isBlank() || !CODE_PATTERN.matcher(code).matches()) {
			return null;
		}
		return code;
	}

	private boolean isSupportedContentType(String contentType) {
		if (contentType == null) {
			return false;
		}
		return SUPPORTED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT));
	}

	private String dataUrl(String contentType, byte[] imageBytes) {
		return "data:" + contentType.toLowerCase(Locale.ROOT) + ";base64,"
				+ Base64.getEncoder().encodeToString(imageBytes);
	}

	private String imageDetail() {
		String configured = properties.getOpenai().getImageDetail();
		return configured == null || configured.isBlank() ? "auto" : configured;
	}
}
