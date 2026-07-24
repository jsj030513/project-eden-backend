package com.projecteden.dataset;

import java.util.regex.Pattern;

public record VisionDatasetId(String value) {
	private static final Pattern SAFE = Pattern.compile("[a-z0-9][a-z0-9-_]{0,63}");
	public VisionDatasetId { if (value == null || !SAFE.matcher(value).matches()) throw new IllegalArgumentException("Invalid dataset id"); }
}
