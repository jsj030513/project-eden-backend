package com.projecteden.dataset;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
public class FilesystemReviewQueueManager implements ReviewQueueManager {

	private final DatasetPathResolver paths;
	private final ObjectMapper yaml;
	private final GroundTruthEditor editor = new GroundTruthEditor();

	public FilesystemReviewQueueManager(@Value("${eden.dataset.root:}") String configuredRoot, ObjectMapper ignored) {
		this(configuredRoot == null || configuredRoot.isBlank()
				? Path.of(System.getenv().getOrDefault("EDEN_DATASET_ROOT", System.getProperty("user.home") + "/.project-eden/datasets"))
				: Path.of(configuredRoot));
	}

	FilesystemReviewQueueManager(Path root) {
		this.paths = new DatasetPathResolver(root);
		this.yaml = new ObjectMapper(new YAMLFactory())
				.registerModule(new JavaTimeModule())
				.setSerializationInclusion(JsonInclude.Include.NON_NULL)
				.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
	}

	@Override
	public ReviewItem enqueue(VisionDatasetId datasetId, VisionDatasetCaseId caseId, VisionGroundTruth prediction, String reviewer, String notes) {
		requireCase(datasetId, caseId);
		if (findByCaseId(datasetId, caseId).isPresent()) {
			throw new IllegalArgumentException("REVIEW_ALREADY_EXISTS_FOR_CASE");
		}
		Instant now = Instant.now();
		ReviewItem item = new ReviewItem(null, nextReviewId(datasetId), datasetId, caseId, now, now, ReviewStatus.PENDING,
				prediction, null, reviewer, notes, List.of());
		write(item);
		return item;
	}

	@Override
	public Optional<ReviewItem> find(VisionDatasetId datasetId, String reviewId) {
		return read(paths.reviewFile(datasetId, reviewId), ReviewItem.class);
	}

	@Override
	public List<ReviewItem> listPending(VisionDatasetId datasetId) {
		return list(datasetId).stream().filter(item -> item.status() == ReviewStatus.PENDING).toList();
	}

	@Override
	public ReviewItem approve(VisionDatasetId datasetId, String reviewId, String reviewer, String notes) {
		ReviewItem current = required(datasetId, reviewId);
		if (current.status() == ReviewStatus.APPROVED) {
			return current;
		}
		requirePending(current);
		GroundTruthEditResult event = editor.approved(current.prediction(), reviewer, notes);
		ReviewItem approved = updated(current, ReviewStatus.APPROVED, event.groundTruth(), reviewer, notes, event);
		write(approved);
		return approved;
	}

	@Override
	public ReviewItem correct(VisionDatasetId datasetId, String reviewId, VisionGroundTruth groundTruth, String reviewer, String notes) {
		ReviewItem current = required(datasetId, reviewId);
		GroundTruthEditResult event = editor.correct(current, groundTruth, reviewer, notes);
		ReviewItem corrected = updated(current, ReviewStatus.CORRECTED, event.groundTruth(), reviewer, notes, event);
		write(corrected);
		return corrected;
	}

	@Override
	public ReviewItem editGroundTruth(VisionDatasetId datasetId, String reviewId, GroundTruthPatch patch, String editedBy) {
		ReviewItem current = required(datasetId, reviewId);
		GroundTruthEditResult event = editor.edit(current, patch, editedBy);
		ReviewItem corrected = updated(current, ReviewStatus.CORRECTED, event.groundTruth(), editedBy, event.notes(), event);
		write(corrected);
		return corrected;
	}

	@Override
	public ReviewItem reject(VisionDatasetId datasetId, String reviewId, String reviewer, String notes) {
		ReviewItem current = required(datasetId, reviewId);
		if (current.status() == ReviewStatus.REJECTED) {
			return current;
		}
		requirePending(current);
		ReviewItem rejected = updated(current, ReviewStatus.REJECTED, null, reviewer, notes, null);
		write(rejected);
		return rejected;
	}

	List<ReviewItem> list(VisionDatasetId datasetId) {
		Path directory = paths.reviewDirectory(datasetId);
		if (!Files.isDirectory(directory)) {
			return List.of();
		}
		try (var files = Files.list(directory)) {
			return files.filter(path -> path.getFileName().toString().endsWith(".yml"))
					.map(path -> read(path, ReviewItem.class))
					.flatMap(Optional::stream)
					.sorted(Comparator.comparing(ReviewItem::createdAt).thenComparing(ReviewItem::reviewId))
					.toList();
		} catch (IOException exception) {
			throw new IllegalStateException("REVIEW_READ_FAILED", exception);
		}
	}

	private void requireCase(VisionDatasetId datasetId, VisionDatasetCaseId caseId) {
		if (read(paths.caseDirectory(datasetId, caseId).resolve("case.yml"), VisionDatasetCase.class).isEmpty()) {
			throw new IllegalArgumentException("CASE_NOT_FOUND");
		}
	}

	private Optional<ReviewItem> findByCaseId(VisionDatasetId datasetId, VisionDatasetCaseId caseId) {
		return list(datasetId).stream().filter(item -> item.caseId().equals(caseId)).findFirst();
	}

	private ReviewItem required(VisionDatasetId datasetId, String reviewId) {
		return find(datasetId, reviewId).orElseThrow(() -> new IllegalArgumentException("REVIEW_NOT_FOUND"));
	}

	private void requirePending(ReviewItem item) {
		if (item.status() != ReviewStatus.PENDING) {
			throw new IllegalArgumentException("INVALID_REVIEW_TRANSITION");
		}
	}

	private ReviewItem updated(ReviewItem item, ReviewStatus status, VisionGroundTruth groundTruth, String reviewer, String notes, GroundTruthEditResult event) {
		List<GroundTruthEditResult> history = new java.util.ArrayList<>(item.history());
		if (event != null) history.add(event);
		return new ReviewItem(item.schemaVersion(), item.reviewId(), item.datasetId(), item.caseId(), item.createdAt(), Instant.now(),
				status, item.prediction(), groundTruth, reviewer, notes, history);
	}

	private String nextReviewId(VisionDatasetId datasetId) {
		int next = list(datasetId).size() + 1;
		return String.format("review-%04d", next);
	}

	private void write(ReviewItem item) {
		Path target = paths.reviewFile(item.datasetId(), item.reviewId());
		try {
			Files.createDirectories(target.getParent());
			Path temporary = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
			yaml.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), item);
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException exception) {
			throw new IllegalStateException("REVIEW_WRITE_FAILED", exception);
		}
	}

	private <T> Optional<T> read(Path path, Class<T> type) {
		if (!Files.exists(path)) {
			return Optional.empty();
		}
		try {
			return Optional.of(yaml.readValue(path.toFile(), type));
		} catch (IOException exception) {
			throw new IllegalStateException("REVIEW_READ_FAILED", exception);
		}
	}
}
