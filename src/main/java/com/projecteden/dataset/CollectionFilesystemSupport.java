package com.projecteden.dataset;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

final class CollectionFilesystemSupport {
	private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9][a-z0-9-_]{0,63}");
	private static final Map<CollectionDimension, Set<String>> VALUES = Map.of(
			CollectionDimension.LIGHTING, Set.of("BRIGHT", "NORMAL", "DARK", "BACKLIT"),
			CollectionDimension.DISTANCE, Set.of("CLOSE", "MEDIUM", "FAR"),
			CollectionDimension.ANGLE, Set.of("FRONT", "SIDE", "TOP", "LOW", "MIXED"),
			CollectionDimension.BACKGROUND, Set.of("SIMPLE", "CLUTTERED", "NATURAL", "INDOOR", "OUTDOOR"),
			CollectionDimension.IMAGE_QUALITY, Set.of("CLEAR", "BLURRY", "LOW_LIGHT", "PARTIALLY_OCCLUDED"),
			CollectionDimension.SUBJECT_COUNT, Set.of("SINGLE", "MULTIPLE"),
			CollectionDimension.SEASON, Set.of("SPRING", "SUMMER", "AUTUMN", "WINTER", "UNKNOWN"),
			CollectionDimension.INDOOR_OUTDOOR, Set.of("INDOOR", "OUTDOOR", "MIXED"));

	private CollectionFilesystemSupport() { }
	static ObjectMapper yaml() { return new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule())
				.setSerializationInclusion(JsonInclude.Include.NON_NULL).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS); }
	static void requireSafeId(String value, String code) { if (value == null || !SAFE_ID.matcher(value).matches()) throw new IllegalArgumentException(code); }
	static Map<CollectionDimension, String> dimensions(Map<CollectionDimension, String> source) {
		Map<CollectionDimension, String> result = new EnumMap<>(CollectionDimension.class);
		if (source == null) return result;
		for (var entry : source.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null || !VALUES.get(entry.getKey()).contains(entry.getValue())) throw new IllegalArgumentException("INVALID_COLLECTION_DIMENSION");
			result.put(entry.getKey(), entry.getValue());
		}
		return result;
	}
	static List<String> codes(List<String> source, String code) {
		TreeSet<String> result = new TreeSet<>();
		if (source != null) for (String value : source) {
			if (value == null || !value.matches("[A-Z0-9][A-Z0-9_-]{0,63}")) throw new IllegalArgumentException(code);
			result.add(value);
		}
		if (source != null && result.size() != source.size()) throw new IllegalArgumentException(code);
		return List.copyOf(result);
	}
	static List<String> planIds(List<String> source) {
		TreeSet<String> result = new TreeSet<>();
		if (source != null) for (String value : source) { requireSafeId(value, "INVALID_COLLECTION_PLAN_ID"); result.add(value); }
		if (source != null && result.size() != source.size()) throw new IllegalArgumentException("DUPLICATE_COLLECTION_PLAN_ID");
		return List.copyOf(result);
	}
	static void atomicWrite(Path target, Object value) {
		try {
			Files.createDirectories(target.getParent());
			if (Files.isSymbolicLink(target.getParent())) throw new IllegalArgumentException("SYMLINK_PATH_NOT_ALLOWED");
			Path temporary = target.resolveSibling(".tmp-" + target.getFileName());
			Files.write(temporary, yaml().writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
			try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
			catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
		} catch (IOException exception) { throw new IllegalStateException("COLLECTION_METADATA_WRITE_FAILED", exception); }
	}
	static <T> T read(Path target, Class<T> type, String missingCode) {
		if (!Files.isRegularFile(target) || Files.isSymbolicLink(target)) throw new IllegalArgumentException(missingCode);
		try { return yaml().readValue(target.toFile(), type); } catch (IOException exception) { throw new IllegalStateException("COLLECTION_METADATA_READ_FAILED", exception); }
	}
	static BigDecimal percent(int matched, int target) { return BigDecimal.valueOf(matched).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(target), 2, RoundingMode.HALF_UP); }
	static Instant now() { return Instant.now(); }
}
