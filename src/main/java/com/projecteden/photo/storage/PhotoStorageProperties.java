package com.projecteden.photo.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "eden.photo-storage")
public class PhotoStorageProperties {

	private String root = System.getProperty("user.home") + "/.project-eden/uploads/photos";

	public String getRoot() {
		return root;
	}

	public void setRoot(String root) {
		this.root = root;
	}
}
