package com.projecteden.photo.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.projecteden.imagenormalization.DefaultImageNormalizationService;
import com.projecteden.imagenormalization.ImageFormatDetector;
import com.projecteden.imagenormalization.ImageNormalizationProperties;

class PhotoUploadValidatorTests {

	private final ImageFormatDetector detector = new ImageFormatDetector();
	private final PhotoUploadValidator validator = new PhotoUploadValidator(
			detector,
			new DefaultImageNormalizationService(detector, new ImageNormalizationProperties()));

	@Test
	void validPngIsCanonicalizedWithoutTrustingDirectorySegments() throws Exception {
		ValidatedPhotoUpload result = validator.validate(new MockMultipartFile(
				"file", "../../private/memory.PNG", "image/png", imageBytes("png", true)));

		assertThat(result.originalFileName()).isEqualTo("memory.PNG");
		assertThat(result.contentType()).isEqualTo("image/png");
		assertThat(result.extension()).isEqualTo("png");
		assertThat(result.size()).isEqualTo(result.bytes().length);
	}

	@Test
	void emptyUploadIsRejected() {
		assertThatThrownBy(() -> validator.validate(new MockMultipartFile(
				"file", "empty.jpg", "image/jpeg", new byte[0])))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("인식할 사진 파일이 필요합니다.");
	}

	@Test
	void declaredMimeMustMatchDetectedBytes() throws Exception {
		assertThatThrownBy(() -> validator.validate(new MockMultipartFile(
				"file", "memory.jpg", "image/png", imageBytes("jpg", false))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("이미지 형식과 Content-Type이 일치하지 않습니다.");
	}

	@Test
	void extensionMustMatchDetectedBytes() throws Exception {
		assertThatThrownBy(() -> validator.validate(new MockMultipartFile(
				"file", "memory.png", "image/jpeg", imageBytes("jpg", false))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("이미지 형식과 파일 확장자가 일치하지 않습니다.");
	}

	@Test
	void corruptRecognizedFormatIsRejected() {
		assertThatThrownBy(() -> validator.validate(new MockMultipartFile(
				"file", "memory.jpg", "image/jpeg",
				new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3})))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("이미지를 읽을 수 없습니다.");
	}

	private byte[] imageBytes(String format, boolean alpha) throws Exception {
		BufferedImage image = new BufferedImage(
				2, 2, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, 0xff224466);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		assertThat(ImageIO.write(image, format, output)).isTrue();
		return output.toByteArray();
	}
}
