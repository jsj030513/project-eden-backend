package com.projecteden.photo.storage;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PhotoStorageWebConfig implements WebMvcConfigurer {

	private final PhotoStorageService photoStorageService;

	public PhotoStorageWebConfig(PhotoStorageService photoStorageService) {
		this.photoStorageService = photoStorageService;
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/uploads/photos/**")
				.addResourceLocations(photoStorageService.storageRoot().toUri().toString());
	}
}
