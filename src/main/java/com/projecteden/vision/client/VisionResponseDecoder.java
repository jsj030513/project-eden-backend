package com.projecteden.vision.client;

import java.util.HashSet;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.projecteden.vision.dto.VisionClassifyResponse;
import com.projecteden.vision.exception.EdenVisionUpstreamException;
import com.projecteden.vision.exception.EdenVisionUpstreamException.Kind;

/** Mirrors RankingResponse/RankingResult, including Python's defaults for omitted fields. */
final class VisionResponseDecoder {
	private final ObjectMapper mapper;

	VisionResponseDecoder(ObjectMapper objectMapper) {
		mapper = objectMapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
				DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
		mapper.configure(MapperFeature.ALLOW_COERCION_OF_SCALARS, false);
		// Jackson otherwise accepts numeric/boolean values for Python's strict text fields.
		for (var shape : new CoercionInputShape[] {CoercionInputShape.Integer,
				CoercionInputShape.Float, CoercionInputShape.Boolean}) {
			mapper.coercionConfigFor(LogicalType.Textual).setCoercion(shape, CoercionAction.Fail);
		}
	}

	VisionClassifyResponse decode(String body) {
		try {
			require(body != null && !body.isBlank());
			ObjectNode root = (ObjectNode) mapper.readTree(body);
			defaultText(root, "schema_version", "1.1");
			defaultText(root, "status", "ranked");
			defaultText(root, "unknown_decision", "not_applied");
			if (!root.has("predictions")) root.putArray("predictions");
			ObjectNode meta = (ObjectNode) root.required("meta");
			defaultText(meta, "policy_version", "not_applied");
			defaultText(meta, "coverage", "not_exhaustive");
			for (var item : root.required("ranking")) {
				require(item.has("broad_category")); // Required, but nullable in Python.
				defaultText((ObjectNode) item, "score_kind", "raw_image_text_logit");
			}
			VisionClassifyResponse result = mapper.treeToValue(root, VisionClassifyResponse.class);
			validate(result);
			return result;
		} catch (Exception ex) {
			// Jackson's exceptions may embed raw payloads; do not retain them as causes.
			throw new EdenVisionUpstreamException(Kind.MALFORMED_RESPONSE);
		}
	}

	private void defaultText(ObjectNode node, String field, String value) {
		if (!node.has(field)) node.put(field, value);
	}

	private void validate(VisionClassifyResponse result) {
		require(text(result.requestId()) && "1.1".equals(result.schemaVersion())
				&& "ranked".equals(result.status()) && "not_applied".equals(result.unknownDecision()));
		require(result.image() != null && positive(result.image().width()) && positive(result.image().height()));
		require(result.predictions() != null && result.predictions().isEmpty());
		var meta = result.meta();
		require(meta != null && text(meta.modelId()) && text(meta.modelRevision()) && text(meta.taxonomyVersion())
				&& "not_applied".equals(meta.policyVersion()) && meta.calibrationVersion() == null
				&& "not_exhaustive".equals(meta.coverage()));
		require(result.ranking() != null && !result.ranking().isEmpty());
		var concepts = new HashSet<String>();
		double previousScore = Double.POSITIVE_INFINITY;
		int rank = 1;
		for (var item : result.ranking()) {
			require(item.rank() != null && item.rank() == rank++ && text(item.conceptId())
					&& text(item.displayName()) && concepts.add(item.conceptId())
					&& item.rawScore() != null && Double.isFinite(item.rawScore())
					&& item.rawScore() <= previousScore && "raw_image_text_logit".equals(item.scoreKind()));
			var hierarchy = item.hierarchy();
			require(hierarchy != null && !hierarchy.isEmpty() && hierarchy.stream().allMatch(this::text)
					&& !hierarchy.contains("UNKNOWN") && new HashSet<>(hierarchy).size() == hierarchy.size()
					&& hierarchy.getLast().equals(item.conceptId()));
			require(item.broadCategory() == null || text(item.broadCategory()) && hierarchy.contains(item.broadCategory()));
			previousScore = item.rawScore();
		}
	}

	private boolean positive(Integer value) { return value != null && value > 0; }
	private boolean text(String value) { return value != null && !value.isBlank(); }
	private void require(boolean condition) {
		if (!condition) throw new IllegalArgumentException("Invalid ranking contract");
	}
}
