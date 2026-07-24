package com.projecteden.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RevisionImageResolverTests {
	@TempDir Path root;

	@Test void resolvesManifestSelectedImageOnly() throws Exception {
		Path revision = revision(); Path selected = revision.resolve("cases/a/image.PNG"); Files.createDirectories(selected.getParent()); Files.write(selected, new byte[] {1}); Files.write(revision.resolve("cases/a/other.jpg"), new byte[] {2});
		assertThat(new RevisionImageResolver(root).resolve(id(), "rev-000001", "cases/a/image.PNG")).isEqualTo(selected);
	}
	@Test void acceptsJpegExtensionsAndRejectsMissingOrUnsupportedFiles() throws Exception {
		Path revision=revision(); Files.createDirectories(revision.resolve("cases/a")); Files.write(revision.resolve("cases/a/a.JPEG"),new byte[]{1}); Files.write(revision.resolve("cases/a/a.gif"),new byte[]{1}); RevisionImageResolver resolver=new RevisionImageResolver(root);
		assertThat(resolver.resolve(id(),"rev-000001","cases/a/a.JPEG").getFileName().toString()).isEqualTo("a.JPEG");
		assertThatThrownBy(()->resolver.resolve(id(),"rev-000001","cases/a/missing.png")).hasMessage("REVISION_IMAGE_NOT_FOUND");
		assertThatThrownBy(()->resolver.resolve(id(),"rev-000001","cases/a/a.gif")).hasMessage("UNSUPPORTED_IMAGE_FORMAT");
	}
	@Test void rejectsAbsoluteUriAndTraversalPaths() throws Exception {
		revision(); RevisionImageResolver resolver=new RevisionImageResolver(root);
		for(String path: new String[]{"/tmp/image.jpg","C:\\images\\a.jpg","file:///tmp/a.jpg","../a.jpg","cases/a/../../../a.jpg"}) assertThatThrownBy(()->resolver.resolve(id(),"rev-000001",path)).hasMessage("REVISION_PATH_INVALID");
	}
	@Test void rejectsSymbolicLinkedTargetWhenSupported() throws Exception {
		Path revision=revision(); Path outside=Files.createTempFile("outside", ".jpg"); Path link=revision.resolve("cases/a/image.jpg"); Files.createDirectories(link.getParent());
		try { Files.createSymbolicLink(link,outside); } catch (UnsupportedOperationException | java.nio.file.FileSystemException e) { org.junit.jupiter.api.Assumptions.abort("symlink unavailable"); }
		assertThatThrownBy(()->new RevisionImageResolver(root).resolve(id(),"rev-000001","cases/a/image.jpg")).hasMessage("SYMLINK_NOT_ALLOWED");
	}
	private Path revision() throws Exception { Path p=root.resolve("datasets/eden/revisions/rev-000001"); Files.createDirectories(p); return p; }
	private VisionDatasetId id(){return new VisionDatasetId("eden");}
}
