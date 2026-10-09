package com.projecteden.vision.client;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecteden.vision.dto.VisionClassifyResponse;
import com.projecteden.vision.exception.EdenVisionUpstreamException;
import com.projecteden.vision.exception.EdenVisionUpstreamException.Kind;

public class EdenVisionClient {
	private final RestClient restClient;
	private final VisionResponseDecoder decoder;

	public EdenVisionClient(RestClient restClient, ObjectMapper objectMapper) {
		this.restClient = restClient;
		this.decoder = new VisionResponseDecoder(objectMapper);
	}

	public VisionClassifyResponse classify(MultipartFile image) {
		if (image == null || image.isEmpty()) throw new IllegalArgumentException("Image is required");
		HttpHeaders headers = new HttpHeaders();
		try {
			headers.setContentType(image.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM
					: MediaType.parseMediaType(image.getContentType()));
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("Invalid image content type");
		}
		var multipart = new LinkedMultiValueMap<String, Object>();
		// MultipartFile's Resource retains filename and streams bytes without a getBytes() copy.
		multipart.add("image", new HttpEntity<>(image.getResource(), headers));
		try {
			String body = restClient.post().uri("/v1/vision/classify")
					.contentType(MediaType.MULTIPART_FORM_DATA).accept(MediaType.APPLICATION_JSON)
					.body(multipart).retrieve()
					.onStatus(status -> !status.is2xxSuccessful(), (request, response) -> {
						throw new EdenVisionUpstreamException(Kind.HTTP_ERROR, response.getStatusCode().value());
					}).body(String.class);
			return decoder.decode(body);
		} catch (ResourceAccessException ex) {
			throw new EdenVisionUpstreamException(isTimeout(ex) ? Kind.TIMEOUT : Kind.CONNECTION_FAILURE);
		} catch (RestClientException ex) {
			// Reading a response body can wrap I/O failures in RestClientException instead.
			if (isTimeout(ex)) throw new EdenVisionUpstreamException(Kind.TIMEOUT);
			if (hasIoCause(ex)) throw new EdenVisionUpstreamException(Kind.CONNECTION_FAILURE);
			throw new EdenVisionUpstreamException(Kind.MALFORMED_RESPONSE);
		}
	}

	private boolean isTimeout(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) return true;
		}
		return false;
	}

	private boolean hasIoCause(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof IOException) return true;
		}
		return false;
	}
}
