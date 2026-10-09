package com.projecteden.vision.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projecteden.vision.exception.EdenVisionUpstreamException;
import com.projecteden.vision.exception.EdenVisionUpstreamException.Kind;

class VisionResponseDecoderTests {
	private final ObjectMapper mapper = new ObjectMapper();
	private final VisionResponseDecoder decoder = new VisionResponseDecoder(mapper);

	private ObjectNode fixture() throws Exception {
		return (ObjectNode) mapper.readTree(new ClassPathResource("vision/ranking-1.1.json")
				.getContentAsString(StandardCharsets.UTF_8));
	}

	private static ObjectNode first(ObjectNode root) { return (ObjectNode) root.path("ranking").get(0); }
	private static ObjectNode meta(ObjectNode root) { return (ObjectNode) root.path("meta"); }

	static Stream<Consumer<ObjectNode>> invalidContracts() {
		return Stream.of(
				r -> r.put("schema_version", "1.0"), r -> r.putNull("schema_version"),
				r -> r.put("status", "classified"), r -> r.put("unknown_decision", "unknown"),
				r -> r.putNull("unknown_decision"), r -> r.put("request_id", " "),
				r -> r.putArray("ranking"), r -> r.putNull("ranking"),
				r -> r.putArray("predictions").addObject(), r -> r.putNull("predictions"),
				r -> first(r).put("rank", 0), r -> first(r).put("rank", 2),
				r -> first(r).put("rank", 1.5), r -> first(r).put("rank", "1"),
				r -> ((ObjectNode) r.path("ranking").get(1)).put("rank", 1),
				r -> first(r).put("concept_id", " "), r -> first(r).put("display_name", ""),
				r -> first(r).put("display_name", 123), r -> first(r).put("display_name", true),
				r -> first(r).putArray("hierarchy").add(123).add("animal").add("cat"),
				r -> first(r).remove("raw_score"), r -> first(r).put("raw_score", "27.1"),
				r -> first(r).put("raw_score", true), r -> first(r).put("raw_score", 1),
				r -> first(r).put("raw_score", Double.POSITIVE_INFINITY),
				r -> first(r).put("score_kind", "confidence"), r -> first(r).putNull("score_kind"),
				r -> first(r).putArray("hierarchy"), r -> first(r).putArray("hierarchy").add("cat").add("cat"),
				r -> first(r).putArray("hierarchy").add("UNKNOWN").add("cat"),
				r -> first(r).put("broad_category", "outside"), r -> first(r).remove("broad_category"),
				r -> first(r).put("concept_id", "dog"),
				r -> {
					first(r).put("concept_id", "dog");
					first(r).putArray("hierarchy").add("living_thing").add("animal").add("dog");
				},
				r -> ((ObjectNode) r.path("image")).put("width", 0),
				r -> ((ObjectNode) r.path("image")).put("height", 1.5),
				r -> meta(r).put("model_id", ""), r -> meta(r).put("policy_version", "applied"),
				r -> meta(r).put("calibration_version", "v1"), r -> meta(r).put("coverage", "exhaustive"),
				r -> r.put("unexpected", "value"));
	}

	@ParameterizedTest
	@MethodSource("invalidContracts")
	void rejectsInvalidContract(Consumer<ObjectNode> mutation) throws Exception {
		ObjectNode root = fixture();
		mutation.accept(root);
		assertThatThrownBy(() -> decoder.decode(root.toString())).isInstanceOfSatisfying(
				EdenVisionUpstreamException.class, ex -> {
					assertThat(ex.getKind()).isEqualTo(Kind.MALFORMED_RESPONSE);
					assertThat(ex.getCause()).isNull();
				});
	}

	@Test
	void acceptsPythonDefaultsNullableCategoryAndEqualScoresWithoutReordering() throws Exception {
		ObjectNode root = fixture();
		root.remove(java.util.List.of("schema_version", "status", "predictions", "unknown_decision"));
		meta(root).remove(java.util.List.of("policy_version", "coverage", "calibration_version"));
		first(root).remove("score_kind");
		first(root).putNull("broad_category");
		first(root).put("raw_score", 25.2);
		var result = decoder.decode(root.toString());
		assertThat(result.schemaVersion()).isEqualTo("1.1");
		assertThat(result.predictions()).isEmpty();
		assertThat(result.meta().coverage()).isEqualTo("not_exhaustive");
		assertThat(result.ranking().getFirst().broadCategory()).isNull();
		assertThat(result.ranking()).extracting(item -> item.conceptId()).containsExactly("cat", "animal", "dog", "tree", "plant");
		assertThat(result.ranking().getFirst().rawScore()).isEqualTo(25.2);
	}

	@Test
	void doesNotImposeFiveItemLimitOnTheSchema() throws Exception {
		ObjectNode root = fixture();
		var ranking = (com.fasterxml.jackson.databind.node.ArrayNode) root.path("ranking");
		while (ranking.size() > 1) ranking.remove(ranking.size() - 1);
		assertThat(decoder.decode(root.toString()).ranking()).hasSize(1);
	}

	@Test
	void rejectsTrailingJson() throws Exception {
		assertThatThrownBy(() -> decoder.decode(fixture() + " {}"))
				.isInstanceOf(EdenVisionUpstreamException.class);
	}
}
