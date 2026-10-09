package com.projecteden.vision.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties("eden.vision-api")
public class EdenVisionApiProperties {

	@NotNull
	private URI baseUrl;
	@NotNull
	private Duration connectTimeout = Duration.ofSeconds(3);
	@NotNull
	private Duration readTimeout = Duration.ofSeconds(30);

	@AssertTrue(message = "base-url must be an HTTP(S) URI with a host and without credentials, query or fragment")
	public boolean isBaseUrlValid() {
		return baseUrl != null && ("http".equalsIgnoreCase(baseUrl.getScheme())
				|| "https".equalsIgnoreCase(baseUrl.getScheme())) && baseUrl.getHost() != null
				&& baseUrl.getUserInfo() == null && baseUrl.getQuery() == null && baseUrl.getFragment() == null
				&& (baseUrl.getPort() == -1 || baseUrl.getPort() > 0 && baseUrl.getPort() <= 65535);
	}

	@AssertTrue(message = "timeouts must be between 1ms and 2147483647ms")
	public boolean isTimeoutsValid() {
		return validTimeout(connectTimeout) && validTimeout(readTimeout);
	}

	private boolean validTimeout(Duration timeout) {
		return timeout != null && timeout.compareTo(Duration.ofMillis(1)) >= 0
				&& timeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) <= 0;
	}

	public URI getBaseUrl() { return baseUrl; }
	public void setBaseUrl(URI baseUrl) { this.baseUrl = baseUrl; }
	public Duration getConnectTimeout() { return connectTimeout; }
	public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
	public Duration getReadTimeout() { return readTimeout; }
	public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
}
