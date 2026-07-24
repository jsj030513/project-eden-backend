package com.projecteden.memorytaxonomy.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageEvaluationReportWriterTests {

	private final ImageEvaluationReportWriter writer = new ImageEvaluationReportWriter(new ImageEvaluationMetrics());

	@TempDir
	Path tempDir;

	@Test
	void writesCsvAndMarkdownWithoutImagePaths() throws Exception {
		ImageEvaluationResult result = new ImageEvaluationResult(
				"case-1",
				"image/jpeg",
				100,
				"OPENAI",
				"model",
				true,
				false,
				"ANIMAL",
				List.of("WALK"),
				List.of("DOG"),
				BigDecimal.valueOf(0.91),
				12,
				"ANIMAL",
				true,
				List.of("WALK"),
				1,
				0,
				0,
				List.of("DOG"),
				1,
				0,
				0,
				null);

		writer.write(tempDir, List.of(result));

		String csv = Files.readString(tempDir.resolve("eden-vision-evaluation-v2.csv"));
		String markdown = Files.readString(tempDir.resolve("eden-vision-evaluation-v2.md"));
		assertThat(csv).contains("case-1").doesNotContain("/tmp").doesNotContain("base64");
		assertThat(markdown).contains("Total cases: 1").doesNotContain("/tmp").doesNotContain("base64");
	}
}
