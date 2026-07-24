package com.projecteden.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecteden.imagenormalization.ImageNormalizationService;

class FilesystemVisionDatasetManagerContextTests {

	@TempDir
	Path root;

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(DatasetTestConfiguration.class);

	@Test
	void managerIsAbsentWhenDisabledOrUnconfigured() {
		contextRunner.run(context -> assertThat(context).doesNotHaveBean(FilesystemVisionDatasetManager.class));
		contextRunner.withPropertyValues("eden.dataset.enabled=false")
				.run(context -> assertThat(context).doesNotHaveBean(FilesystemVisionDatasetManager.class));
	}

	@Test
	void managerIsCreatedOnlyWhenExplicitlyEnabledWithConfiguredRoot() {
		contextRunner.withPropertyValues("eden.dataset.enabled=true", "eden.dataset.root=" + root)
				.run(context -> assertThat(context).hasSingleBean(FilesystemVisionDatasetManager.class));
	}

	@Configuration(proxyBeanMethods = false)
	@Import(FilesystemVisionDatasetManager.class)
	static class DatasetTestConfiguration {
		@Bean
		ImageNormalizationService imageNormalizationService() {
			return input -> { throw new UnsupportedOperationException("Not used by context test"); };
		}

		@Bean
		ObjectMapper objectMapper() {
			return new ObjectMapper();
		}
	}
}
