package com.projecteden.vision.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.projecteden.vision.dto.VisionErrorResponse;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class VisionExceptionHandler {
	@ExceptionHandler(VisionRequestException.class)
	public ResponseEntity<VisionErrorResponse> handleRequest(VisionRequestException exception) {
		return switch (exception.getReason()) {
			case DISABLED -> error(HttpStatus.SERVICE_UNAVAILABLE, "VISION_DISABLED", "Vision analysis is disabled.");
			case IMAGE_REQUIRED -> error(HttpStatus.BAD_REQUEST, "VISION_IMAGE_REQUIRED", "An image file is required.");
			case IMAGE_EMPTY -> error(HttpStatus.BAD_REQUEST, "VISION_IMAGE_EMPTY", "The image file is empty.");
			case IMAGE_TOO_LARGE -> imageTooLarge();
			case INVALID_REQUEST -> invalidRequest();
		};
	}

	@ExceptionHandler(EdenVisionUpstreamException.class)
	public ResponseEntity<VisionErrorResponse> handleUpstream(EdenVisionUpstreamException exception) {
		return switch (exception.getKind()) {
			case CONNECTION_FAILURE -> error(HttpStatus.SERVICE_UNAVAILABLE, "VISION_CONNECTION_FAILED",
					"Vision analysis is temporarily unavailable.");
			case TIMEOUT -> error(HttpStatus.GATEWAY_TIMEOUT, "VISION_TIMEOUT", "Vision analysis timed out.");
			case MALFORMED_RESPONSE -> error(HttpStatus.BAD_GATEWAY, "VISION_INVALID_RESPONSE",
					"Vision returned an invalid response.");
			case HTTP_ERROR -> upstreamStatus(exception.getStatusCode());
		};
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<?> handleMultipartLimit(MaxUploadSizeExceededException exception,
			HttpServletRequest request) {
		// Multipart parsing can fail before controller selection, so controller-scoped advice cannot catch it.
		if ("POST".equals(request.getMethod())
				&& (request.getContextPath() + "/api/vision/classify").equals(request.getRequestURI())) {
			return imageTooLarge();
		}
		// Let Spring's remaining resolvers preserve develop's existing non-Vision 413 handling.
		throw exception;
	}

	private ResponseEntity<VisionErrorResponse> upstreamStatus(Integer status) {
		if (status != null) {
			switch (status) {
				case 400: return invalidRequest();
				case 413: return imageTooLarge();
				case 415: return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "VISION_IMAGE_UNSUPPORTED",
						"The image format is unsupported or does not match its content type.");
				case 422: return error(HttpStatus.UNPROCESSABLE_ENTITY, "VISION_IMAGE_INVALID",
						"The image could not be decoded.");
				default: break;
			}
		}
		// Unexpected statuses (including upstream authentication/configuration failures) are gateway errors.
		return error(HttpStatus.BAD_GATEWAY, "VISION_UPSTREAM_ERROR", "Vision analysis could not be completed.");
	}

	private ResponseEntity<VisionErrorResponse> imageTooLarge() {
		// Upstream 413 also covers the decoded pixel limit, not just encoded file size.
		return error(HttpStatus.PAYLOAD_TOO_LARGE, "VISION_IMAGE_TOO_LARGE", "The image exceeds the supported size limit.");
	}

	private ResponseEntity<VisionErrorResponse> invalidRequest() {
		return error(HttpStatus.BAD_REQUEST, "VISION_INVALID_REQUEST", "The Vision image request is invalid.");
	}

	private ResponseEntity<VisionErrorResponse> error(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(new VisionErrorResponse(code, message));
	}
}
