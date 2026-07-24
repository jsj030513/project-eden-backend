package com.projecteden.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecteden.imagenormalization.ImageFormat;
import com.projecteden.imagenormalization.ImageNormalizationService;
import com.projecteden.imagenormalization.NormalizedImage;

class FilesystemVisionDatasetManagerIntegrationTests {
	@TempDir Path root;
	@Test void createsImportsExportsRejectsDuplicateAndArchivesDeterministically() throws Exception {
		ImageNormalizationService normalization = input -> new NormalizedImage("normalized-image".getBytes(), "image/jpeg", ImageFormat.JPEG, 2, 2, ImageFormat.JPEG, 2, 2, false, false, false, false, false, true, "sha-1");
		FilesystemVisionDatasetManager manager = new FilesystemVisionDatasetManager(normalization, new ObjectMapper(), root);
		VisionDatasetId datasetId = new VisionDatasetId("eden-local");
		manager.createDataset(new VisionDataset(null, datasetId, "Eden", 1, "ACTIVE", 0));
		Path source = root.resolve("source.jpg"); Files.write(source, "source".getBytes()); byte[] original = Files.readAllBytes(source);
		VisionDatasetCase imported = manager.importCase(datasetId, input(datasetId, "cat-001"), source);
		assertThat(Files.readAllBytes(source)).isEqualTo(original);
		assertThat(Files.exists(root.resolve("datasets/eden-local/").resolve(imported.relativePath()))).isTrue();
		Path manifest = manager.exportManifest(datasetId);
		assertThat(new com.projecteden.memorytaxonomy.evaluation.ImageEvaluationManifestReader(new ObjectMapper()).read(manifest, 10)).hasSize(1);
		byte[] first = Files.readAllBytes(manifest); for (int i=0;i<20;i++) assertThat(Files.readAllBytes(manager.exportManifest(datasetId))).isEqualTo(first);
		assertThatThrownBy(() -> manager.importCase(datasetId, input(datasetId, "cat-002"), source)).hasMessageContaining("DUPLICATE_IMAGE");
		assertThat(Files.exists(root.resolve("datasets/eden-local/cases/cat-002"))).isFalse();
		manager.archiveCase(datasetId, new VisionDatasetCaseId("cat-001"));
		assertThat(new com.projecteden.memorytaxonomy.evaluation.ImageEvaluationManifestReader(new ObjectMapper()).read(manager.exportManifest(datasetId), 10)).isEmpty();
		assertThat(Files.exists(root.resolve("datasets/eden-local/cases/cat-001/image.jpg"))).isTrue();
	}

	@Test
	void rejectsSymbolicLinkSourceWithoutCreatingCaseDirectory() throws Exception {
		ImageNormalizationService normalization = input -> new NormalizedImage("normalized-image".getBytes(), "image/jpeg", ImageFormat.JPEG, 2, 2, ImageFormat.JPEG, 2, 2, false, false, false, false, false, true, "sha-1");
		FilesystemVisionDatasetManager manager = new FilesystemVisionDatasetManager(normalization, new ObjectMapper(), root);
		VisionDatasetId datasetId = new VisionDatasetId("eden-local");
		manager.createDataset(new VisionDataset(null, datasetId, "Eden", 1, "ACTIVE", 0));
		Path actual = root.resolve("actual.jpg");
		Path sourceLink = root.resolve("source-link.jpg");
		Files.write(actual, "source".getBytes());
		try {
			Files.createSymbolicLink(sourceLink, actual);
		} catch (UnsupportedOperationException | java.nio.file.FileSystemException exception) {
			org.junit.jupiter.api.Assumptions.abort("Symbolic links are unavailable in this environment");
		}

		assertThatThrownBy(() -> manager.importCase(datasetId, input(datasetId, "cat-001"), sourceLink))
				.hasMessageContaining("SOURCE_SYMLINK_NOT_ALLOWED");
		assertThat(Files.exists(root.resolve("datasets/eden-local/cases/cat-001"))).isFalse();
	}
	private VisionDatasetCase input(VisionDatasetId id, String caseId) { return new VisionDatasetCase(null,new VisionDatasetCaseId(caseId),id,null,null,0,0,null,new VisionConsentMetadata(true,true,false,false,Instant.EPOCH,"v1"),new VisionGroundTruth(List.of("CAT"),List.of(),List.of(),"ANIMAL",List.of("CAT"),false),"CONFIRMED",null); }
}
