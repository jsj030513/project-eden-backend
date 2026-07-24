package com.projecteden.photo.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.projecteden.photo.dto.PhotoResponse;
import com.projecteden.photo.dto.PhotoUploadResponse;
import com.projecteden.photo.service.PhotoService;
import com.projecteden.user.domain.User;

@RestController
@RequestMapping("/api/photos")
public class PhotoController {

	private final PhotoService photoService;

	public PhotoController(PhotoService photoService) {
		this.photoService = photoService;
	}

	@PostMapping
	public ResponseEntity<PhotoUploadResponse> uploadPhoto(
			@AuthenticationPrincipal User user,
			@RequestParam(value = "plantId", required = false) Long plantId,
			@RequestParam("file") MultipartFile file) {
		PhotoUploadResponse response = photoService.uploadPhoto(user.getId(), plantId, file);
		return ResponseEntity.created(URI.create("/api/photos/" + response.photoId())).body(response);
	}

	@GetMapping("/me")
	public ResponseEntity<List<PhotoResponse>> getMyPhotos(@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(photoService.getMyPhotos(user.getId()));
	}
}
