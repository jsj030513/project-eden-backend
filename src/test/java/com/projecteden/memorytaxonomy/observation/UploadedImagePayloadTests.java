package com.projecteden.memorytaxonomy.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class UploadedImagePayloadTests {

	@Test
	void createsPayloadFromMultipartFileWithoutExposingBytesInToString() {
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"cat.jpg",
				"image/jpeg",
				"fake-image".getBytes());

		UploadedImagePayload payload = UploadedImagePayload.from(file);

		assertThat(payload.originalFileName()).isEqualTo("cat.jpg");
		assertThat(payload.contentType()).isEqualTo("image/jpeg");
		assertThat(payload.size()).isEqualTo(10);
		assertThat(payload.bytes()).isEqualTo("fake-image".getBytes());
		assertThat(payload.toString()).contains("bytesPresent=true");
		assertThat(payload.toString()).doesNotContain("fake-image");
	}

	@Test
	void imageObservationRequestCanUseUploadedPayload() {
		UploadedImagePayload payload = UploadedImagePayload.of(
				"flower.jpg",
				"image/jpeg",
				12,
				"flower-bytes".getBytes());

		ImageObservationRequest request = ImageObservationRequest.of(
				1L,
				"old.jpg",
				"image/png",
				5,
				null);
		ImageObservationRequest withPayload = ImageObservationRequest.of(
				request.photoId(),
				payload.originalFileName(),
				payload.contentType(),
				payload.size(),
				payload.bytes());

		assertThat(withPayload.originalFileName()).isEqualTo("flower.jpg");
		assertThat(withPayload.contentType()).isEqualTo("image/jpeg");
		assertThat(withPayload.fileSize()).isEqualTo(12);
		assertThat(withPayload.hasImageBytes()).isTrue();
		assertThat(withPayload.toString()).doesNotContain("flower-bytes");
	}

	@Test
	void emptyMultipartFileIsRejected() {
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"empty.jpg",
				"image/jpeg",
				new byte[0]);

		assertThatThrownBy(() -> UploadedImagePayload.from(file))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("인식할 사진 파일이 필요합니다.");
	}
}
