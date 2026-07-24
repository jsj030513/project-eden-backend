package com.projecteden.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class GlobalExceptionHandlerTests {

	private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

	@Test
	void oversizedPhotoUploadReturnsPayloadTooLarge() {
		ResponseEntity<Map<String, String>> response =
				exceptionHandler.handleMaxUploadSizeExceededException(
						new MaxUploadSizeExceededException(15 * 1024 * 1024L));

		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals(
				"사진의 크기가 너무 큽니다. 조금 더 작은 사진으로 다시 시도해 주세요.",
				response.getBody().get("message"));
	}
}
