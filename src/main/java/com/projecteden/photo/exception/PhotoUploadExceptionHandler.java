package com.projecteden.photo.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.projecteden.photo.controller.PhotoController;

@RestControllerAdvice(assignableTypes = PhotoController.class)
public class PhotoUploadExceptionHandler {
	@ExceptionHandler(PhotoUploadTooLargeException.class)
	public ResponseEntity<Map<String, String>> handleTooLarge(PhotoUploadTooLargeException exception) {
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
				.body(Map.of("message", exception.getMessage()));
	}
}
