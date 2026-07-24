package com.projecteden.dataset;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Component
@ConditionalOnProperty(prefix = "eden.dataset", name = "enabled", havingValue = "true")
public class FilesystemDatasetVersionManager implements DatasetVersionManager {

	private final DatasetPathResolver paths;
	private final ObjectMapper yaml;

	public FilesystemDatasetVersionManager(@Value("${eden.dataset.root:}") String configuredRoot, ObjectMapper ignored) {
		this(configuredRoot == null || configuredRoot.isBlank()
				? Path.of(System.getenv().getOrDefault("EDEN_DATASET_ROOT", System.getProperty("user.home") + "/.project-eden/datasets"))
				: Path.of(configuredRoot));
	}

	FilesystemDatasetVersionManager(Path root) {
		this.paths = new DatasetPathResolver(root);
		this.yaml = new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule())
				.setSerializationInclusion(JsonInclude.Include.NON_NULL)
				.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
	}

	@Override
	public DatasetRevision createRevision(VisionDatasetId datasetId, RevisionMetadata metadata) {
		Path datasetFile = paths.dataset(datasetId).resolve("dataset.yml");
		Path manifestFile = paths.dataset(datasetId).resolve("manifests/evaluation-v2.yml");
		if (!Files.isRegularFile(datasetFile)) throw new IllegalArgumentException("DATASET_NOT_FOUND");
		if (!Files.isRegularFile(manifestFile)) throw new IllegalArgumentException("MANIFEST_NOT_FOUND");
		try {
			byte[] datasetBytes = Files.readAllBytes(datasetFile);
			byte[] manifestBytes = revisionManifest(datasetId, manifestFile);
			DatasetSnapshot snapshot = snapshot(datasetId, datasetBytes);
			byte[] summaryBytes = yaml.writerWithDefaultPrettyPrinter().writeValueAsBytes(snapshot);
			String datasetChecksum = sha256(datasetBytes);
			String manifestChecksum = sha256(manifestBytes);
			String summaryChecksum = sha256(summaryBytes);
			if (list(datasetId).stream().anyMatch(revision -> revision.datasetChecksum().equals(datasetChecksum)
					&& revision.manifestChecksum().equals(manifestChecksum) && revision.summaryChecksum().equals(summaryChecksum))) {
				throw new IllegalArgumentException("DUPLICATE_REVISION_SNAPSHOT");
			}
			String revisionId = String.format("rev-%06d", list(datasetId).size() + 1);
			DatasetRevision revision = new DatasetRevision(null, revisionId, datasetId, Instant.now(),
					metadata == null ? new RevisionMetadata(null, null) : metadata, snapshot.caseCount(), manifestChecksum, datasetChecksum,
					summaryChecksum, RevisionStatus.ACTIVE);
			writeSnapshot(datasetId, revision, datasetBytes, manifestBytes, summaryBytes);
			return revision;
		} catch (IOException exception) {
			throw new IllegalStateException("REVISION_SNAPSHOT_FAILED", exception);
		}
	}

	@Override
	public Optional<DatasetRevision> find(VisionDatasetId datasetId, String revisionId) {
		return read(paths.revisionDirectory(datasetId, revisionId).resolve("revision.yml"), DatasetRevision.class);
	}

	@Override
	public List<DatasetRevision> list(VisionDatasetId datasetId) {
		Path directory = paths.revisionDirectory(datasetId);
		if (!Files.isDirectory(directory)) return List.of();
		try (var files = Files.list(directory)) {
			return files.filter(Files::isDirectory)
					.map(path -> read(path.resolve("revision.yml"), DatasetRevision.class))
					.flatMap(Optional::stream)
					.sorted(Comparator.comparing(DatasetRevision::revisionId))
					.toList();
		} catch (IOException exception) {
			throw new IllegalStateException("REVISION_READ_FAILED", exception);
		}
	}

	private DatasetSnapshot snapshot(VisionDatasetId datasetId, byte[] datasetBytes) throws IOException {
		VisionDataset dataset = yaml.readValue(datasetBytes, VisionDataset.class);
		EnumMap<ReviewStatus, Integer> statusCount = new EnumMap<>(ReviewStatus.class);
		for (ReviewStatus status : ReviewStatus.values()) statusCount.put(status, 0);
		Map<String, Integer> categories = new TreeMap<>();
		Map<String, Integer> tags = new TreeMap<>();
		Path reviews = paths.reviewDirectory(datasetId);
		if (Files.isDirectory(reviews)) try (var files = Files.list(reviews)) {
			for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".yml")).toList()) {
				ReviewItem review = yaml.readValue(file.toFile(), ReviewItem.class);
				statusCount.compute(review.status(), (key, count) -> count + 1);
				if (review.publishesGroundTruth()) {
					VisionGroundTruth groundTruth = review.groundTruth();
					if (groundTruth.category() != null) categories.merge(groundTruth.category(), 1, Integer::sum);
					groundTruth.tags().forEach(tag -> tags.merge(tag, 1, Integer::sum));
				}
			}
		}
		return new DatasetSnapshot(dataset.caseCount(), statusCount.get(ReviewStatus.APPROVED), statusCount.get(ReviewStatus.CORRECTED),
				statusCount.get(ReviewStatus.REJECTED), statusCount.get(ReviewStatus.PENDING), categories, tags);
	}

	private void writeSnapshot(VisionDatasetId datasetId, DatasetRevision revision, byte[] datasetBytes, byte[] manifestBytes, byte[] summaryBytes) throws IOException {
		Path target = paths.revisionDirectory(datasetId, revision.revisionId());
		if (Files.exists(target)) throw new IllegalArgumentException("REVISION_ALREADY_EXISTS");
		Path temporary = paths.revisionDirectory(datasetId).resolve("." + revision.revisionId() + "-" + UUID.randomUUID());
		Files.createDirectories(temporary);
		Files.write(temporary.resolve("dataset.yml"), datasetBytes);
		Files.write(temporary.resolve("manifest.yml"), manifestBytes);
		Files.write(temporary.resolve("summary.yml"), summaryBytes);
		copyManifestImages(datasetId, manifestBytes, temporary);
		yaml.writerWithDefaultPrettyPrinter().writeValue(temporary.resolve("revision.yml").toFile(), revision);
		try {
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(temporary, target);
		}
	}

	private byte[] revisionManifest(VisionDatasetId datasetId, Path manifestFile) throws IOException {
		com.fasterxml.jackson.databind.JsonNode root = yaml.readTree(manifestFile.toFile());
		com.fasterxml.jackson.databind.node.ArrayNode cases = root.has("cases") ? (com.fasterxml.jackson.databind.node.ArrayNode) root.get("cases") : (com.fasterxml.jackson.databind.node.ArrayNode) root;
		for (com.fasterxml.jackson.databind.JsonNode item : cases) {
			String caseId = item.path("caseId").asText(); String path = item.path("imagePath").asText();
			Path source = sourceImage(datasetId, path); String name = source.getFileName().toString(); String extension = name.substring(name.lastIndexOf('.'));
			((com.fasterxml.jackson.databind.node.ObjectNode) item).put("imagePath", "images/" + caseId + extension.toLowerCase(java.util.Locale.ROOT));
		}
		return yaml.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
	}

	private void copyManifestImages(VisionDatasetId datasetId, byte[] manifestBytes, Path temporary) throws IOException {
		com.fasterxml.jackson.databind.JsonNode root = yaml.readTree(manifestBytes); com.fasterxml.jackson.databind.node.ArrayNode cases = root.has("cases") ? (com.fasterxml.jackson.databind.node.ArrayNode) root.get("cases") : (com.fasterxml.jackson.databind.node.ArrayNode) root;
		Path images = temporary.resolve("images"); Files.createDirectories(images);
		for (com.fasterxml.jackson.databind.JsonNode item : cases) {
			String caseId=item.path("caseId").asText(); Path source=paths.caseDirectory(datasetId,new VisionDatasetCaseId(caseId)).resolve("image.jpg");
			if (!Files.isRegularFile(source)) { try (var files=Files.list(paths.caseDirectory(datasetId,new VisionDatasetCaseId(caseId)))) { source=files.filter(Files::isRegularFile).findFirst().orElseThrow(()->new IllegalArgumentException("REVISION_IMAGE_NOT_FOUND")); } }
			String expected=item.path("imagePath").asText(); Files.copy(source, temporary.resolve(expected), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private Path sourceImage(VisionDatasetId datasetId, String raw) {
		if (raw==null||raw.isBlank()||raw.startsWith("file:")||raw.matches("^[A-Za-z]:[\\\\/].*")) throw new IllegalArgumentException("REVISION_PATH_INVALID");
		Path relative=Path.of(raw); if(relative.isAbsolute()) throw new IllegalArgumentException("REVISION_PATH_INVALID"); Path dataset=paths.dataset(datasetId); Path source=dataset.resolve("manifests").resolve(relative).normalize(); if(!source.startsWith(dataset)||Files.isSymbolicLink(source)||!Files.isRegularFile(source)) throw new IllegalArgumentException("REVISION_IMAGE_NOT_FOUND"); return source;
	}

	private String sha256(byte[] bytes) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
			StringBuilder value = new StringBuilder();
			for (byte item : digest) value.append(String.format("%02x", item));
			return value.toString();
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA_256_UNAVAILABLE", exception);
		}
	}

	private <T> Optional<T> read(Path path, Class<T> type) {
		if (!Files.isRegularFile(path)) return Optional.empty();
		try {
			return Optional.of(yaml.readValue(path.toFile(), type));
		} catch (IOException exception) {
			throw new IllegalStateException("REVISION_READ_FAILED", exception);
		}
	}
}
