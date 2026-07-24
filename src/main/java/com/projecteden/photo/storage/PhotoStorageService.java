package com.projecteden.photo.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.projecteden.memorytaxonomy.observation.UploadedImagePayload;
import com.projecteden.photo.domain.Photo;

@Service
public class PhotoStorageService {

	private final PhotoStorageProperties properties;

	public PhotoStorageService(PhotoStorageProperties properties) {
		this.properties = properties;
	}

	public void store(String storedFileName, MultipartFile file) {
		Path target = resolve(storedFileName);
		Path root = storageRoot();
		Path temporary = root.resolve("." + storedFileName + "." + UUID.randomUUID() + ".tmp");

		try {
			Files.createDirectories(root);
			Files.copy(file.getInputStream(), temporary, StandardCopyOption.REPLACE_EXISTING);
			moveAtomically(temporary, target);
		} catch (IOException exception) {
			deleteQuietly(temporary);
			throw new IllegalStateException("사진 파일을 저장할 수 없습니다.", exception);
		}
	}

	public Optional<UploadedImagePayload> load(Photo photo) {
		Path source = resolve(photo.getStoredFileName());
		if (!Files.isRegularFile(source) || Files.isSymbolicLink(source)) {
			return Optional.empty();
		}

		try {
			byte[] bytes = Files.readAllBytes(source);
			return Optional.of(UploadedImagePayload.of(
					photo.getOriginalFileName(), photo.getContentType(), bytes.length, bytes));
		} catch (IOException exception) {
			throw new IllegalStateException("저장된 사진 파일을 읽을 수 없습니다.", exception);
		}
	}

	public Path storageRoot() {
		return Path.of(properties.getRoot()).toAbsolutePath().normalize();
	}

	private Path resolve(String storedFileName) {
		if (storedFileName == null || storedFileName.isBlank()
				|| !storedFileName.equals(Path.of(storedFileName).getFileName().toString())) {
			throw new IllegalArgumentException("안전하지 않은 사진 파일 이름입니다.");
		}
		Path root = storageRoot();
		Path resolved = root.resolve(storedFileName).normalize();
		if (!resolved.startsWith(root)) {
			throw new IllegalArgumentException("안전하지 않은 사진 저장 경로입니다.");
		}
		return resolved;
	}

	private void moveAtomically(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
			// The original storage exception remains the actionable failure.
		}
	}
}
