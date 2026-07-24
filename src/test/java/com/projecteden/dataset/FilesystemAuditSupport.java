package com.projecteden.dataset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

final class FilesystemAuditSupport {
	private FilesystemAuditSupport() { }
	static Map<String,String> snapshotChecksums(Path dataset) throws IOException {
		Map<String,String> result=new TreeMap<>(); try(var paths=Files.walk(dataset)){ for(Path path:paths.filter(Files::isRegularFile).toList()){ String key=dataset.relativize(path).toString().replace('\\','/'); if(!key.startsWith("benchmarks/")) result.put(key,sha256(path)); } } return result;
	}
	static Map<String,String> snapshotChecksumsIncludingBenchmarks(Path root) throws IOException { Map<String,String> result=new TreeMap<>(); try(var paths=Files.walk(root)){for(Path path:paths.filter(Files::isRegularFile).toList())result.put(root.relativize(path).toString().replace('\\','/'),sha256(path));}return result; }
	static Set<String> snapshotRelativePaths(Path root) throws IOException { Set<String> result=new TreeSet<>(); try(var paths=Files.walk(root)){paths.filter(Files::isRegularFile).forEach(path->result.add(root.relativize(path).toString().replace('\\','/')));}return result;}
	static Set<String> findTemporaryEntries(Path root) throws IOException { Set<String> result=new TreeSet<>(); try(var paths=Files.walk(root)){paths.filter(path->!path.equals(root)).forEach(path->{String name=path.getFileName().toString(); if(name.endsWith(".tmp")||name.endsWith(".temp")||name.endsWith(".part")||name.endsWith(".pending")||name.endsWith(".bak")||name.startsWith(".tmp-")||name.startsWith(".temp-")||name.endsWith(".swp"))result.add(root.relativize(path).toString().replace('\\','/'));});}return result;}
	static String sha256(Path path) throws IOException { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
}
