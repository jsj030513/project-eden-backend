package com.projecteden.vision.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecteden.vision.client.EdenVisionClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "eden.vision-api", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(EdenVisionApiProperties.class)
public class EdenVisionClientConfig {

	@Bean
	EdenVisionClient edenVisionClient(RestClient.Builder builder, ObjectMapper objectMapper,
			EdenVisionApiProperties properties) {
		SimpleClientHttpRequestFactory factory = requestFactory(properties);
		RestClient client = builder.clone().baseUrl(properties.getBaseUrl().toString())
				.requestFactory(factory).build();
		return new EdenVisionClient(client, objectMapper);
	}

	static SimpleClientHttpRequestFactory requestFactory(EdenVisionApiProperties properties) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(properties.getConnectTimeout());
		factory.setReadTimeout(properties.getReadTimeout());
		return factory;
	}
}
