package com.projecteden.dataset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatasetPathResolverTests {

	@TempDir
	Path root;

	@Test
	void rejectsAbsoluteAndTraversalPaths() {
		DatasetPathResolver resolver = new DatasetPathResolver(root);
		assertThatThrownBy(() -> resolver.safe(Path.of("..", "outside"))).hasMessageContaining("Path escape");
		assertThatThrownBy(() -> resolver.safe(root.resolve("outside"))).hasMessageContaining("Absolute child path");
	}

	@Test
	void rejectsManagedPathThroughSymbolicLink() throws Exception {
		Path outside = Files.createTempDirectory("dataset-outside-");
		try {
			Files.createSymbolicLink(root.resolve("datasets"), outside);
		} catch (UnsupportedOperationException | java.nio.file.FileSystemException exception) {
			Assumptions.abort("Symbolic links are unavailable in this environment");
		}

		DatasetPathResolver resolver = new DatasetPathResolver(root);
		assertThatThrownBy(() -> resolver.safe(Path.of("datasets", "eden", "case.yml")))
				.hasMessageContaining("Symlink paths are not allowed");
	}
}
