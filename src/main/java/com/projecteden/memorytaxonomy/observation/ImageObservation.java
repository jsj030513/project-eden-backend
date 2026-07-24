package com.projecteden.memorytaxonomy.observation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class ImageObservation {

	private final List<String> subjects;
	private final List<String> objects;
	private final String scene;
	private final List<String> activities;
	private final List<String> relationships;
	private final List<String> moodSignals;
	private final String provider;
	private final String modelVersion;
	private final BigDecimal confidence;
	private final boolean recognized;
	private final boolean fallback;

	private ImageObservation(
			List<String> subjects,
			List<String> objects,
			String scene,
			List<String> activities,
			List<String> relationships,
			List<String> moodSignals,
			String provider,
			String modelVersion,
			BigDecimal confidence,
			boolean recognized,
			boolean fallback) {
		this.subjects = immutableDistinct(subjects);
		this.objects = immutableDistinct(objects);
		this.scene = scene;
		this.activities = immutableDistinct(activities);
		this.relationships = immutableDistinct(relationships);
		this.moodSignals = immutableDistinct(moodSignals);
		this.provider = provider;
		this.modelVersion = modelVersion;
		this.confidence = confidence;
		this.recognized = recognized;
		this.fallback = fallback;
	}

	public static ImageObservation recognized(
			List<String> subjects,
			List<String> objects,
			String scene,
			List<String> activities,
			List<String> relationships,
			List<String> moodSignals,
			String provider,
			String modelVersion,
			BigDecimal confidence) {
		return new ImageObservation(
				subjects,
				objects,
				scene,
				activities,
				relationships,
				moodSignals,
				provider,
				modelVersion,
				confidence,
				true,
				false);
	}

	public static ImageObservation fallback(String provider, String modelVersion) {
		return new ImageObservation(
				List.of(),
				List.of(),
				null,
				List.of(),
				List.of(),
				List.of(),
				provider,
				modelVersion,
				BigDecimal.ZERO,
				false,
				true);
	}

	/** A technically successful observation where no specific object was found. */
	public static ImageObservation generalMemory(String provider, String modelVersion) {
		return new ImageObservation(
				List.of(),
				List.of("OBJECT"),
				null,
				List.of(),
				List.of(),
				List.of(),
				provider,
				modelVersion,
				BigDecimal.ZERO,
				true,
				true);
	}

	public Map<String, Object> toMap() {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("subjects", subjects);
		values.put("objects", objects);
		values.put("scene", scene);
		values.put("activities", activities);
		values.put("relationships", relationships);
		values.put("moodSignals", moodSignals);
		values.put("provider", provider);
		values.put("modelVersion", modelVersion);
		values.put("confidence", confidence);
		values.put("recognized", recognized);
		values.put("fallback", fallback);
		return Collections.unmodifiableMap(values);
	}

	private static List<String> immutableDistinct(List<String> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		return Collections.unmodifiableList(new ArrayList<>(new LinkedHashSet<>(values)));
	}

	public List<String> subjects() {
		return subjects;
	}

	public List<String> objects() {
		return objects;
	}

	public String scene() {
		return scene;
	}

	public List<String> activities() {
		return activities;
	}

	public List<String> relationships() {
		return relationships;
	}

	public List<String> moodSignals() {
		return moodSignals;
	}

	public String provider() {
		return provider;
	}

	public String modelVersion() {
		return modelVersion;
	}

	public BigDecimal confidence() {
		return confidence;
	}

	public boolean recognized() {
		return recognized;
	}

	public boolean fallback() {
		return fallback;
	}
}
