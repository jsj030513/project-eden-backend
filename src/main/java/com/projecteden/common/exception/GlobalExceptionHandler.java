package com.projecteden.common.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, String>> handleValidationException(
			MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(error -> error.getDefaultMessage())
				.orElse("잘못된 요청입니다.");
		return ResponseEntity.badRequest().body(Map.of("message", message));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> handleIllegalArgumentException(
			IllegalArgumentException exception) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Map.of("message", exception.getMessage()));
	}

	@ExceptionHandler(AuthenticationFailureException.class)
	public ResponseEntity<Map<String, String>> handleAuthenticationFailureException(
			AuthenticationFailureException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("message", exception.getMessage()));
	}

	@ExceptionHandler(DuplicateResourceException.class)
	public ResponseEntity<Map<String, String>> handleDuplicateResourceException(
			DuplicateResourceException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(Map.of("message", exception.getMessage()));
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<Map<String, String>> handleResourceNotFoundException(
			ResourceNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(Map.of("message", exception.getMessage()));
	}

	@ExceptionHandler(ForbiddenOperationException.class)
	public ResponseEntity<Map<String, String>> handleForbiddenOperationException(
			ForbiddenOperationException exception) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(Map.of("message", exception.getMessage()));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Map<String, String>> handleDataIntegrityViolationException(
			DataIntegrityViolationException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(Map.of("message", "이미 존재하거나 사용할 수 없는 값입니다."));
	}
}
