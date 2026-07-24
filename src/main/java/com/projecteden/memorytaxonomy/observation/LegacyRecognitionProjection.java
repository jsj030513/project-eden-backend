package com.projecteden.memorytaxonomy.observation;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.ai.dto.RecognitionResult;

@Component
public class LegacyRecognitionProjection {

	private static final Map<String, RecognizedObject> SIGNALS = new LinkedHashMap<>();

	static {
		for (RecognizedObject object : RecognizedObject.values()) {
			SIGNALS.put(object.name(), object);
		}
		SIGNALS.put("STUDYING", RecognizedObject.STUDY);
		SIGNALS.put("WORKING", RecognizedObject.WORKSPACE);
		// COCO animal subclasses without legacy enum entries remain meaningful
		// generic animal memories rather than becoming UNKNOWN.
		SIGNALS.put("HORSE", RecognizedObject.ANIMAL);
		SIGNALS.put("SHEEP", RecognizedObject.ANIMAL);
		SIGNALS.put("COW", RecognizedObject.ANIMAL);
		SIGNALS.put("TEDDY_BEAR", RecognizedObject.OBJECT);
		SIGNALS.put("WILD_ANIMAL", RecognizedObject.ANIMAL);
		SIGNALS.put("FARM_ANIMAL", RecognizedObject.ANIMAL);
		SIGNALS.put("VEGETABLE", RecognizedObject.VEGETABLE);
		SIGNALS.put("PLANT", RecognizedObject.PLANT);
		SIGNALS.put("VEHICLE", RecognizedObject.OBJECT);
		SIGNALS.put("OUTDOOR", RecognizedObject.LANDSCAPE);
		SIGNALS.put("BAG", RecognizedObject.DAILY_OBJECT);
		SIGNALS.put("SPORT", RecognizedObject.DAILY_OBJECT);
		SIGNALS.put("KITCHEN", RecognizedObject.FOOD);
		SIGNALS.put("TOY", RecognizedObject.OBJECT);
		SIGNALS.put("CLOTHING", RecognizedObject.DAILY_OBJECT);
		SIGNALS.put("BUILDING", RecognizedObject.ROOM);
		SIGNALS.put("OTHER_OBJECT", RecognizedObject.OBJECT);
		SIGNALS.put("UNKNOWN_OBJECT", RecognizedObject.OBJECT);
	}

	public RecognitionResult project(ImageObservation observation) {
		if (observation == null || !observation.recognized()) {
			return RecognitionResult.unknown();
		}
		if (observation.fallback() && observation.objects().equals(java.util.List.of("OBJECT"))) {
			return RecognitionResult.generalMemory();
		}
		RecognizedObject object = firstRecognizedObject(observation);
		if (object == null || object == RecognizedObject.UNKNOWN) {
			return RecognitionResult.unknown();
		}
		return RecognitionResult.recognized(
				object,
				observation.confidence().movePointRight(2).intValue());
	}

	private RecognizedObject firstRecognizedObject(ImageObservation observation) {
		for (String subject : observation.subjects()) {
			RecognizedObject object = SIGNALS.get(subject);
			if (object != null) {
				return object;
			}
		}
		for (String objectSignal : observation.objects()) {
			RecognizedObject object = SIGNALS.get(objectSignal);
			if (object != null) {
				return object;
			}
		}
		if (observation.scene() != null) {
			RecognizedObject object = SIGNALS.get(observation.scene());
			if (object != null) {
				return object;
			}
		}
		for (String activity : observation.activities()) {
			RecognizedObject object = SIGNALS.get(activity);
			if (object != null) {
				return object;
			}
		}
		for (String relationship : observation.relationships()) {
			RecognizedObject object = SIGNALS.get(relationship);
			if (object != null) {
				return object;
			}
		}
		return null;
	}
}
