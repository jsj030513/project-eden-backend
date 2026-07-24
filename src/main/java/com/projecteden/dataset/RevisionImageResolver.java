package com.projecteden.dataset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves exactly the image path declared by an immutable revision manifest. */
public final class RevisionImageResolver {
	private final DatasetPathResolver paths;
	public RevisionImageResolver(Path root) { this.paths = new DatasetPathResolver(root); }
	public Path resolve(VisionDatasetId datasetId, String revisionId, String manifestImagePath) {
		if (manifestImagePath == null || manifestImagePath.isBlank() || manifestImagePath.startsWith("file:") || manifestImagePath.matches("^[A-Za-z]:[\\\\/].*")) throw new IllegalArgumentException("REVISION_PATH_INVALID");
		Path relative = Path.of(manifestImagePath);
		if (relative.isAbsolute()) throw new IllegalArgumentException("REVISION_PATH_INVALID");
		Path revision = paths.revisionDirectory(datasetId, revisionId);
		Path image = revision.resolve(relative).normalize();
		if (!image.startsWith(revision)) throw new IllegalArgumentException("REVISION_PATH_INVALID");
		Path cursor = revision;
		for (Path part : revision.relativize(image)) {
			cursor = cursor.resolve(part);
			if (Files.isSymbolicLink(cursor)) throw new IllegalArgumentException("SYMLINK_NOT_ALLOWED");
		}
		if (Files.isSymbolicLink(image)) throw new IllegalArgumentException("SYMLINK_NOT_ALLOWED");
		if (!Files.isRegularFile(image)) throw new IllegalArgumentException("REVISION_IMAGE_NOT_FOUND");
		if (!image(image)) throw new IllegalArgumentException("UNSUPPORTED_IMAGE_FORMAT");
		return image;
	}
	private boolean image(Path path) { String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT); return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"); }
}
