package com.projecteden.memorytaxonomy.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

class ImageEvaluationManifestReaderTests {

	@Test
	void readsVersionTwoLayeredExpectedFields(@TempDir Path tempDir) throws Exception {
		Path manifest = tempDir.resolve("v2.json");
		Files.writeString(manifest, """
			{"version":2,"cases":[{"caseId":"cat-001","enabled":true,"imagePath":"/private/cat.jpg","expectedObjects":["CAT","CAT"],"expectedActivities":[],"expectedRelationships":[],"expectedFallback":false}]}
			""");
		var evaluationCase = new ImageEvaluationManifestReader(new ObjectMapper()).read(manifest, 10).getFirst();
		assertThat(evaluationCase.manifestVersion()).isEqualTo(2);
		assertThat(evaluationCase.expectedObjects()).containsExactly("CAT");
		assertThat(evaluationCase.expectedFallback()).isFalse();
	}

	private final ImageEvaluationManifestReader reader = new ImageEvaluationManifestReader(new ObjectMapper());

	@TempDir
	Path tempDir;

	@Test
	void readsManifestAndLimitsCases() throws Exception {
		Path manifest = tempDir.resolve("manifest.json");
		Files.writeString(manifest, """
				[
				  {"caseId":"case-1","imagePath":"/tmp/cat.jpg","expectedPrimary":"ANIMAL","expectedTags":["CAT"]},
				  {"caseId":"case-2","imagePath":"/tmp/flower.jpg","expectedPrimary":"NATURE"}
				]
				""");

		var cases = reader.read(manifest, 1);

		assertThat(cases).hasSize(1);
		assertThat(cases.getFirst().caseId()).isEqualTo("case-1");
		assertThat(cases.getFirst().expectedTags()).containsExactly("CAT");
	}

	@Test
	void rejectsDuplicateCaseId() throws Exception {
		Path manifest = tempDir.resolve("manifest.json");
		Files.writeString(manifest, """
				[
				  {"caseId":"case-1","imagePath":"/tmp/cat.jpg"},
				  {"caseId":"case-1","imagePath":"/tmp/dog.jpg"}
				]
				""");

		assertThatThrownBy(() -> reader.read(manifest, 10))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("caseId가 중복");
	}
}
