package com.projecteden.memorytaxonomy.observation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImageObservationRequestTests {

	@Test
	void protectsImageBytesFromMutationAndToString() {
		byte[] bytes = "secret-image-bytes".getBytes();
		ImageObservationRequest request = ImageObservationRequest.of(
				1L,
				"cat.jpg",
				"image/jpeg",
				bytes.length,
				bytes);

		bytes[0] = 'X';
		byte[] returned = request.imageBytes();
		returned[1] = 'Y';

		assertThat(new String(request.imageBytes())).isEqualTo("secret-image-bytes");
		assertThat(request.toString()).doesNotContain("secret-image-bytes");
		assertThat(request.toString()).contains("imageBytesPresent=true");
	}
}
