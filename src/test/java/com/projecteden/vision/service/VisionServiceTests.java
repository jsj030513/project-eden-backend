package com.projecteden.vision.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecteden.vision.client.EdenVisionClient;
import com.projecteden.vision.dto.VisionClassifyResponse;
import com.projecteden.vision.exception.EdenVisionUpstreamException;
import com.projecteden.vision.exception.EdenVisionUpstreamException.Kind;
import com.projecteden.vision.exception.VisionRequestException;
import com.projecteden.vision.exception.VisionRequestException.Reason;

class VisionServiceTests {
	private final EdenVisionClient client = mock(EdenVisionClient.class);
	private final VisionService service = new VisionService(true, Optional.of(client));

	@Test
	void disabledDoesNotCallClientEvenWhenOneIsAvailable() {
		assertReason(() -> new VisionService(false, Optional.of(client)).classify(image()), Reason.DISABLED);
		verifyNoInteractions(client);
	}

	@Test
	void absentClientDoesNotPreventServiceConstruction() {
		assertReason(() -> new VisionService(false, Optional.empty()).classify(image()), Reason.DISABLED);
		assertReason(() -> new VisionService(true, Optional.empty()).classify(image()), Reason.DISABLED);
	}

	@Test
	void missingImageDoesNotCallClient() {
		assertReason(() -> service.classify(null), Reason.IMAGE_REQUIRED);
		verifyNoInteractions(client);
	}

	@Test
	void emptyImageDoesNotCallClient() {
		assertReason(() -> service.classify(new MockMultipartFile("image", new byte[0])), Reason.IMAGE_EMPTY);
		verifyNoInteractions(client);
	}

	@Test
	void oversizedImageDoesNotCallClient() {
		MultipartFile file = mock(MultipartFile.class);
		when(file.getSize()).thenReturn(10L * 1024 * 1024 + 1);
		assertReason(() -> service.classify(file), Reason.IMAGE_TOO_LARGE);
		verifyNoInteractions(client);
	}

	@ParameterizedTest
	@CsvSource({"photo.heic,application/octet-stream", "photo.heif,image/heif", "photo.mpo,image/jpeg",
			"arbitrary.ext,text/plain", "photo.webp,image/webp", "no-extension,NULL"})
	void forwardsInputExactlyOnceWithoutMimeOrExtensionPolicy(String name, String mime) throws Exception {
		MockMultipartFile file = new MockMultipartFile("image", name, "NULL".equals(mime) ? null : mime, new byte[] {1});
		var response = new ObjectMapper().readValue(new ClassPathResource("vision/ranking-1.1.json")
				.getContentAsString(StandardCharsets.UTF_8), VisionClassifyResponse.class);
		when(client.classify(file)).thenReturn(response);
		assertThat(service.classify(file)).isSameAs(response);
		verify(client).classify(file);
		verifyNoMoreInteractions(client);
	}

	@Test
	void passesUpstreamExceptionToHttpBoundaryUnchanged() {
		var failure = new EdenVisionUpstreamException(Kind.TIMEOUT);
		var file = image();
		when(client.classify(file)).thenThrow(failure);
		assertThatThrownBy(() -> service.classify(file)).isSameAs(failure);
		verify(client).classify(file);
		verifyNoMoreInteractions(client);
	}

	private MockMultipartFile image() { return new MockMultipartFile("image", new byte[] {1}); }

	private void assertReason(Runnable action, Reason reason) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(VisionRequestException.class,
				ex -> assertThat(ex.getReason()).isEqualTo(reason));
	}
}
