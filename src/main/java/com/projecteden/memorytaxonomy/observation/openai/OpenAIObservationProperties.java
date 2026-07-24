package com.projecteden.memorytaxonomy.observation.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "eden.image-observation")
public class OpenAIObservationProperties {

	private String provider = "mock";
	private long maxImageBytes = 10_485_760L;
	private OpenAI openai = new OpenAI();

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public long getMaxImageBytes() {
		return maxImageBytes;
	}

	public void setMaxImageBytes(long maxImageBytes) {
		this.maxImageBytes = maxImageBytes;
	}

	public OpenAI getOpenai() {
		return openai;
	}

	public void setOpenai(OpenAI openai) {
		this.openai = openai;
	}

	public boolean isOpenAIConfigured() {
		return hasText(openai.apiKey) && hasText(openai.model);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	public static class OpenAI {

		private String apiKey = "";
		private String model = "";
		private String baseUrl = "https://api.openai.com/v1";
		private int connectTimeoutMs = 3000;
		private int readTimeoutMs = 15000;
		private int maxRetries = 1;
		private String imageDetail = "auto";

		public String getApiKey() {
			return apiKey;
		}

		public void setApiKey(String apiKey) {
			this.apiKey = apiKey;
		}

		public String getModel() {
			return model;
		}

		public void setModel(String model) {
			this.model = model;
		}

		public String getBaseUrl() {
			return baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}

		public int getConnectTimeoutMs() {
			return connectTimeoutMs;
		}

		public void setConnectTimeoutMs(int connectTimeoutMs) {
			this.connectTimeoutMs = connectTimeoutMs;
		}

		public int getReadTimeoutMs() {
			return readTimeoutMs;
		}

		public void setReadTimeoutMs(int readTimeoutMs) {
			this.readTimeoutMs = readTimeoutMs;
		}

		public int getMaxRetries() {
			return maxRetries;
		}

		public void setMaxRetries(int maxRetries) {
			this.maxRetries = maxRetries;
		}

		public String getImageDetail() {
			return imageDetail;
		}

		public void setImageDetail(String imageDetail) {
			this.imageDetail = imageDetail;
		}
	}
}
