package com.projecteden.photo.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import com.projecteden.character.domain.Character;
import com.projecteden.memorytaxonomy.observation.UploadedImagePayload;
import com.projecteden.photo.domain.Photo;

class PhotoStorageServiceTests {

	@TempDir
	Path temporaryDirectory;

	@Test
	void storesAndLoadsTheOriginalUploadedBytes() throws Exception {
		PhotoStorageProperties properties = new PhotoStorageProperties();
		properties.setRoot(temporaryDirectory.resolve("photos").toString());
		PhotoStorageService storage = new PhotoStorageService(properties);
		byte[] imageBytes = new byte[] { 1, 2, 3, 4 };
		String storedFileName = "photo-001.jpg";

		storage.store(storedFileName, new MockMultipartFile(
				"file", "moment.jpg", "image/jpeg", imageBytes));

		assertThat(Files.readAllBytes(storage.storageRoot().resolve(storedFileName))).isEqualTo(imageBytes);
		Photo photo = Photo.create((Character) null, null, "moment.jpg", storedFileName,
				"image/jpeg", imageBytes.length, "/uploads/photos/" + storedFileName);
		UploadedImagePayload loaded = storage.load(photo).orElseThrow();
		assertThat(loaded.bytes()).isEqualTo(imageBytes);
		assertThat(loaded.contentType()).isEqualTo("image/jpeg");
	}

	@Test
	void unsafeStoredFileNameCannotEscapeStorageRoot() {
		PhotoStorageProperties properties = new PhotoStorageProperties();
		properties.setRoot(temporaryDirectory.toString());
		PhotoStorageService storage = new PhotoStorageService(properties);

		assertThatThrownBy(() -> storage.store("../outside.jpg", new byte[] {1}))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("안전하지 않은 사진 파일 이름입니다.");
		assertThat(Files.exists(temporaryDirectory.getParent().resolve("outside.jpg"))).isFalse();
	}

	@Test
	void storageFailureLeavesNoTemporaryFile() throws Exception {
		Path rootFile = temporaryDirectory.resolve("not-a-directory");
		Files.write(rootFile, new byte[] {1});
		PhotoStorageProperties properties = new PhotoStorageProperties();
		properties.setRoot(rootFile.toString());
		PhotoStorageService storage = new PhotoStorageService(properties);

		assertThatThrownBy(() -> storage.store("photo.jpg", new byte[] {1, 2, 3}))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("사진 파일을 저장할 수 없습니다.");
		try (var entries = Files.list(temporaryDirectory)) {
			assertThat(entries.map(path -> path.getFileName().toString()).toList())
					.containsExactly("not-a-directory");
		}
	}
}
