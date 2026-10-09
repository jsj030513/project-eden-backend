package com.projecteden.vision.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.projecteden.vision.dto.VisionClassifyResponse;
import com.projecteden.vision.service.VisionService;

@RestController
@RequestMapping("/api/vision")
public class VisionController {
	private final VisionService visionService;

	public VisionController(VisionService visionService) {
		this.visionService = visionService;
	}

	@PostMapping(value = "/classify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public VisionClassifyResponse classify(@RequestPart(value = "image", required = false) MultipartFile image) {
		// Missing parts are validated by the service so they receive a stable Vision error code.
		return visionService.classify(image);
	}
}
