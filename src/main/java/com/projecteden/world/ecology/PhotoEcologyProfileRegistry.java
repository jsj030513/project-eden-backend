package com.projecteden.world.ecology;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.projecteden.ai.domain.RecognizedObject;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class PhotoEcologyProfileRegistry {
    public static final int VERSION = 1;
    private final Map<RecognizedObject, PhotoEcologyProfile> profiles = new EnumMap<>(RecognizedObject.class);
    private String checksum;

    @PostConstruct
    void load() {
        ClassPathResource resource = new ClassPathResource("world/photo-ecology-profiles-v1.yml");
        try (InputStream input = resource.getInputStream()) {
            byte[] bytes = input.readAllBytes();
            RegistryFile file = new ObjectMapper(new YAMLFactory()).readValue(bytes, RegistryFile.class);
            if (file.version() != VERSION) throw new IllegalStateException("ECOLOGY_PROFILE_VERSION_MISMATCH");
            if (file.profiles() == null || file.profiles().isEmpty()) throw new IllegalStateException("ECOLOGY_PROFILES_REQUIRED");
            for (PhotoEcologyProfile profile : file.profiles()) validateAndRegister(profile);
            for (RecognizedObject type : RecognizedObject.values()) {
                if (!profiles.containsKey(type)) throw new IllegalStateException("MISSING_ECOLOGY_PROFILE_" + type);
            }
            checksum = sha256(bytes);
        } catch (IOException exception) {
            throw new IllegalStateException("ECOLOGY_PROFILE_LOAD_FAILED", exception);
        }
    }

    public PhotoEcologyProfile require(RecognizedObject type) {
        PhotoEcologyProfile profile = profiles.get(type == null ? RecognizedObject.UNKNOWN : type);
        if (profile == null) throw new IllegalArgumentException("ECOLOGY_PROFILE_NOT_FOUND_" + type);
        return profile;
    }

    public String checksum() { return checksum; }
    public int profileCount() { return profiles.size(); }

    private void validateAndRegister(PhotoEcologyProfile profile) {
        if (profile.profileKey() == null || profile.profileKey().isBlank()) throw new IllegalStateException("ECOLOGY_PROFILE_KEY_REQUIRED");
        if (profile.version() != VERSION) throw new IllegalStateException("ECOLOGY_PROFILE_ENTRY_VERSION_MISMATCH_" + profile.profileKey());
        if (profile.recognitionTypes() == null || profile.recognitionTypes().isEmpty()) throw new IllegalStateException("ECOLOGY_RECOGNITION_TYPES_REQUIRED_" + profile.profileKey());
        if (profile.ecologyCategory() == null || profile.projectedAssetType() == null || profile.fallbackPolicy() == null) throw new IllegalStateException("ECOLOGY_PROFILE_CONTRACT_REQUIRED_" + profile.profileKey());
        if (profile.maxPerChunk() <= 0 || profile.maxPerZone() <= 0) throw new IllegalStateException("ECOLOGY_CAPACITY_MUST_BE_POSITIVE_" + profile.profileKey());
        if (profile.preferredRegions() == null || profile.allowedRegions() == null || !profile.allowedRegions().containsAll(profile.preferredRegions())) throw new IllegalStateException("ECOLOGY_PREFERRED_REGION_NOT_ALLOWED_" + profile.profileKey());
        if (profile.preferredTerrain() == null || profile.allowedTerrain() == null || !profile.allowedTerrain().containsAll(profile.preferredTerrain())) throw new IllegalStateException("ECOLOGY_PREFERRED_TERRAIN_NOT_ALLOWED_" + profile.profileKey());
        if (profile.spawnZoneTags() == null || profile.spawnZoneTags().isEmpty()) throw new IllegalStateException("ECOLOGY_SPAWN_ZONE_REQUIRED_" + profile.profileKey());
        for (RecognizedObject type : profile.recognitionTypes()) {
            if (profiles.putIfAbsent(type, profile) != null) throw new IllegalStateException("DUPLICATE_ECOLOGY_PROFILE_" + type);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record RegistryFile(int version, List<PhotoEcologyProfile> profiles) { }
}
