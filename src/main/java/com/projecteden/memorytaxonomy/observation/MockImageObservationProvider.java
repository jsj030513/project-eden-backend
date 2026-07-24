package com.projecteden.memorytaxonomy.observation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class MockImageObservationProvider implements ImageObservationProvider {

	public static final String PROVIDER = "LEGACY_MOCK";
	public static final String MODEL_VERSION = "mock-filename-v1";

	private static final List<KeywordRule> KEYWORD_RULES = List.of(
			KeywordRule.subject("cat", "CAT"),
			KeywordRule.subject("kitten", "CAT"),
			KeywordRule.subject("neko", "CAT"),
			KeywordRule.subject("dog", "DOG"),
			KeywordRule.subject("puppy", "DOG"),
			KeywordRule.subject("bird", "BIRD"),
			KeywordRule.subject("animal", "ANIMAL"),
			KeywordRule.object("flower", "FLOWER"),
			KeywordRule.object("rose", "FLOWER"),
			KeywordRule.object("tulip", "FLOWER"),
			KeywordRule.object("tree", "TREE"),
			KeywordRule.object("forest", "TREE"),
			KeywordRule.object("plant", "PLANT"),
			KeywordRule.object("sky", "SKY"),
			KeywordRule.object("landscape", "LANDSCAPE"),
			KeywordRule.object("food", "FOOD"),
			KeywordRule.object("bread", "BREAD"),
			KeywordRule.object("cake", "FOOD"),
			KeywordRule.object("fruit", "FRUIT"),
			KeywordRule.object("vegetable", "VEGETABLE"),
			KeywordRule.object("tomato", "TOMATO"),
			KeywordRule.object("carrot", "CARROT"),
			KeywordRule.object("potato", "POTATO"),
			KeywordRule.object("wheat", "WHEAT"),
			KeywordRule.object("water", "WATER"),
			KeywordRule.object("river", "RIVER"),
			KeywordRule.object("sea", "SEA"),
			KeywordRule.object("pond", "POND"),
			KeywordRule.object("road", "ROAD"),
			KeywordRule.object("path", "PATH"),
			KeywordRule.scene("park", "PARK"),
			KeywordRule.object("street", "STREET"),
			KeywordRule.activity("programming", "PROGRAMMING"),
			KeywordRule.activity("developer", "CODING"),
			KeywordRule.activity("development", "CODING"),
			KeywordRule.activity("coding", "CODING"),
			KeywordRule.activity("code", "CODING"),
			KeywordRule.activity("workspace", "WORKSPACE"),
			KeywordRule.object("computer", "COMPUTER"),
			KeywordRule.object("keyboard", "COMPUTER"),
			KeywordRule.object("monitor", "COMPUTER"),
			KeywordRule.object("laptop", "LAPTOP"),
			KeywordRule.activity("meeting", "MEETING"),
			KeywordRule.activity("project", "WORKSPACE"),
			KeywordRule.scene("office", "OFFICE"),
			KeywordRule.object("desk", "DESK"),
			KeywordRule.activity("work", "WORKING", "WORKSPACE"),
			KeywordRule.activity("코딩", "CODING"),
			KeywordRule.activity("개발", "CODING"),
			KeywordRule.object("노트북", "LAPTOP"),
			KeywordRule.object("컴퓨터", "COMPUTER"),
			KeywordRule.object("키보드", "COMPUTER"),
			KeywordRule.object("모니터", "COMPUTER"),
			KeywordRule.object("책상", "DESK"),
			KeywordRule.activity("회의", "MEETING"),
			KeywordRule.activity("프로젝트", "WORKSPACE"),
			KeywordRule.activity("작업", "WORKSPACE"),
			KeywordRule.activity("reading", "READING"),
			KeywordRule.activity("lecture", "LECTURE"),
			KeywordRule.scene("library", "LIBRARY"),
			KeywordRule.activity("writing", "WRITING"),
			KeywordRule.object("notebook", "NOTEBOOK"),
			KeywordRule.activity("study", "STUDYING", "STUDY"),
			KeywordRule.scene("school", "STUDY"),
			KeywordRule.activity("class", "LECTURE"),
			KeywordRule.activity("exam", "STUDY"),
			KeywordRule.activity("test", "STUDY"),
			KeywordRule.activity("memo", "WRITING"),
			KeywordRule.object("note", "NOTEBOOK"),
			KeywordRule.object("book", "BOOK"),
			KeywordRule.activity("read", "READING"),
			KeywordRule.activity("필기", "WRITING"),
			KeywordRule.activity("공부", "STUDYING", "STUDY"),
			KeywordRule.activity("독서", "READING"),
			KeywordRule.object("책", "BOOK"),
			KeywordRule.activity("강의", "LECTURE"),
			KeywordRule.activity("시험", "STUDY"),
			KeywordRule.scene("도서관", "LIBRARY"),
			KeywordRule.object("coffee", "COFFEE"),
			KeywordRule.scene("room", "ROOM"),
			KeywordRule.object("daily", "DAILY_OBJECT"),
			KeywordRule.subject("person", "PERSON"),
			KeywordRule.relationship("friend", "FRIEND"),
			KeywordRule.relationship("family", "FAMILY"),
			KeywordRule.object("object", "OBJECT"));

	@Override
	public ImageObservation observe(ImageObservationRequest request) {
		String fileName = request.originalFileName() == null
				? ""
				: request.originalFileName().toLowerCase(Locale.ROOT);
		return KEYWORD_RULES.stream()
				.filter(rule -> fileName.contains(rule.keyword()))
				.findFirst()
				.map(this::observationFor)
				.orElseGet(() -> ImageObservation.fallback(provider(), modelVersion()));
	}

	@Override
	public String provider() {
		return PROVIDER;
	}

	@Override
	public String modelVersion() {
		return MODEL_VERSION;
	}

	private ImageObservation observationFor(KeywordRule rule) {
		return ImageObservation.recognized(
				rule.type() == SignalType.SUBJECT ? List.of(rule.signal()) : List.of(),
				rule.type() == SignalType.OBJECT ? List.of(rule.signal()) : List.of(),
				rule.type() == SignalType.SCENE ? rule.signal() : null,
				rule.type() == SignalType.ACTIVITY ? List.of(rule.signal()) : List.of(),
				rule.type() == SignalType.RELATIONSHIP ? List.of(rule.signal()) : List.of(),
				List.of(),
				provider(),
				modelVersion(),
				confidenceFor(rule.legacyObjectName()));
	}

	private BigDecimal confidenceFor(String legacyObjectName) {
		return "FLOWER".equals(legacyObjectName)
				? BigDecimal.valueOf(0.95)
				: BigDecimal.valueOf(0.82);
	}

	private enum SignalType {
		SUBJECT,
		OBJECT,
		SCENE,
		ACTIVITY,
		RELATIONSHIP
	}

	private record KeywordRule(
			String keyword,
			SignalType type,
			String signal,
			String legacyObjectName) {

		static KeywordRule subject(String keyword, String signal) {
			return new KeywordRule(keyword, SignalType.SUBJECT, signal, signal);
		}

		static KeywordRule object(String keyword, String signal) {
			return new KeywordRule(keyword, SignalType.OBJECT, signal, signal);
		}

		static KeywordRule scene(String keyword, String signal) {
			return new KeywordRule(keyword, SignalType.SCENE, signal, signal);
		}

		static KeywordRule activity(String keyword, String signal) {
			return new KeywordRule(keyword, SignalType.ACTIVITY, signal, signal);
		}

		static KeywordRule activity(String keyword, String signal, String legacyObjectName) {
			return new KeywordRule(keyword, SignalType.ACTIVITY, signal, legacyObjectName);
		}

		static KeywordRule relationship(String keyword, String signal) {
			return new KeywordRule(keyword, SignalType.RELATIONSHIP, signal, signal);
		}
	}
}
