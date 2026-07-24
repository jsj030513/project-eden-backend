package com.projecteden.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class DatasetCollectionContextTests {
	@TempDir Path root;
	private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(CollectionTestConfiguration.class);
	@Test void collectionBeansRequireBothDatasetAndCollectionProperties() {
		runner.run(context -> assertThat(context).doesNotHaveBean(FilesystemDatasetCollectionPlanManager.class));
		runner.withPropertyValues("eden.dataset.enabled=true", "eden.dataset.collection.enabled=false", "eden.dataset.root=" + root).run(context -> assertThat(context).doesNotHaveBean(FilesystemCollectionCaseMetadataManager.class));
		runner.withPropertyValues("eden.dataset.enabled=true", "eden.dataset.collection.enabled=true", "eden.dataset.root=" + root).run(context -> { assertThat(context).hasSingleBean(FilesystemDatasetCollectionPlanManager.class); assertThat(context).hasSingleBean(FilesystemCollectionCaseMetadataManager.class); });
	}
	@Configuration(proxyBeanMethods = false)
	@Import({FilesystemDatasetCollectionPlanManager.class, FilesystemCollectionCaseMetadataManager.class})
	static class CollectionTestConfiguration { }
}
