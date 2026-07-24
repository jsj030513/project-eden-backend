package com.projecteden.ai.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.projecteden.ai.dto.RecognitionResponse;
import com.projecteden.ai.service.RecognitionApplicationService;
import com.projecteden.user.domain.User;

@RestController
@RequestMapping("/api/photos")
public class RecognitionController {

	private final RecognitionApplicationService recognitionApplicationService;

	public RecognitionController(RecognitionApplicationService recognitionApplicationService) {
		this.recognitionApplicationService = recognitionApplicationService;
	}

	@PostMapping("/{photoId}/recognize")
	public ResponseEntity<RecognitionResponse> recognizePhoto(
			@AuthenticationPrincipal User user,
			@PathVariable Long photoId) {
		return ResponseEntity.ok(recognitionApplicationService.recognizePhoto(user.getId(), photoId));
	}

	@PostMapping("/{photoId}/recognize-with-image")
	public ResponseEntity<RecognitionResponse> recognizePhotoWithImage(
			@AuthenticationPrincipal User user,
			@PathVariable Long photoId,
			@RequestParam("file") MultipartFile file) {
		return ResponseEntity.ok(recognitionApplicationService.recognizePhotoWithImage(
				user.getId(),
				photoId,
				file));
	}

	@GetMapping("/recognitions")
	public ResponseEntity<List<RecognitionResponse>> getMyRecognitions(
			@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(recognitionApplicationService.getMyRecognitions(user.getId()));
	}
}
