package com.projecteden.vision.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartResolver;

import com.projecteden.vision.exception.VisionExceptionHandler;
import com.projecteden.vision.service.VisionService;

class VisionMultipartErrorTests {
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new VisionController(mock(VisionService.class)))
				.setControllerAdvice(new VisionExceptionHandler()).build();
		MultipartResolver resolver = mock(MultipartResolver.class);
		when(resolver.isMultipart(any())).thenReturn(true);
		when(resolver.resolveMultipart(any())).thenThrow(new MaxUploadSizeExceededException(15L * 1024 * 1024));
		ReflectionTestUtils.setField(mockMvc.getDispatcherServlet(), "multipartResolver", resolver);
	}

	@Test
	void globalLimitFailureBeforeControllerSelectionHasVisionCode() throws Exception {
		mockMvc.perform(post("/api/vision/classify").contentType(MediaType.MULTIPART_FORM_DATA))
				.andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("VISION_IMAGE_TOO_LARGE"));
	}

	@Test
	void globalLimitFailureForPhotosRetainsExistingResponse() throws Exception {
		mockMvc.perform(post("/api/photos").contentType(MediaType.MULTIPART_FORM_DATA))
				.andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").doesNotExist());
	}
}
