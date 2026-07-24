package com.projecteden.dataset;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class FilesystemAuditSupportTests {
 @TempDir Path root;
 @Test void producesDeterministicRelativeChecksumsAndFindsMutations() throws Exception { Files.createDirectories(root.resolve("nested")); Files.writeString(root.resolve("nested/a.txt"),"one"); var first=FilesystemAuditSupport.snapshotChecksums(root); assertThat(FilesystemAuditSupport.snapshotChecksums(root)).isEqualTo(first); assertThat(first.keySet()).containsExactly("nested/a.txt"); Files.writeString(root.resolve("nested/a.txt"),"two"); assertThat(FilesystemAuditSupport.snapshotChecksums(root)).isNotEqualTo(first); }
 @Test void excludesBenchmarksButIncludesRunSnapshots() throws Exception { Path run=root.resolve("benchmarks/run-1"); Files.createDirectories(run); Files.writeString(run.resolve("benchmark.yml"),"one"); assertThat(FilesystemAuditSupport.snapshotChecksums(root)).isEmpty(); assertThat(FilesystemAuditSupport.snapshotChecksumsIncludingBenchmarks(run)).containsKey("benchmark.yml"); }
 @Test void detectsAddedRemovedAndTemporaryEntries() throws Exception { Files.writeString(root.resolve("kept.yml"),"one"); var before=FilesystemAuditSupport.snapshotRelativePaths(root); Files.delete(root.resolve("kept.yml")); Files.writeString(root.resolve("added.yml"),"two"); Files.writeString(root.resolve("scratch.tmp"),"x"); Files.writeString(root.resolve(".tmp-write"),"x"); Files.writeString(root.resolve("swap.swp"),"x"); var after=FilesystemAuditSupport.snapshotRelativePaths(root); var added=new java.util.TreeSet<>(after); added.removeAll(before); var removed=new java.util.TreeSet<>(before); removed.removeAll(after); assertThat(added).contains("added.yml"); assertThat(removed).contains("kept.yml"); assertThat(FilesystemAuditSupport.findTemporaryEntries(root)).containsExactly(".tmp-write","scratch.tmp","swap.swp"); }
}
