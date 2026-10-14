package com.projecteden.world.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.projecteden.world.chunk.WorldChunkRegionType;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class RegionTemplateRegistry {

    public static final int GENERATION_VERSION = 3;
    private final RegionTemplateValidator validator;
    private final Map<WorldChunkRegionType, RegionTemplate> templates =
            new EnumMap<>(WorldChunkRegionType.class);
    private String checksum;

    public RegionTemplateRegistry(RegionTemplateValidator validator) {
        this.validator = validator;
    }

    @PostConstruct
    void load() {
        ClassPathResource resource = new ClassPathResource("world/region-templates-v2.yml");
        try (InputStream input = resource.getInputStream()) {
            byte[] bytes = input.readAllBytes();
            RegistryFile file = new ObjectMapper(new YAMLFactory()).readValue(bytes, RegistryFile.class);
            if (file.generationVersion() != GENERATION_VERSION) {
                throw new IllegalStateException("REGION_TEMPLATE_VERSION_MISMATCH");
            }
            for (RegionTemplate template : file.templates()) {
                validator.validate(template);
                if (template.regionType() == WorldChunkRegionType.HUB
                        || templates.putIfAbsent(template.regionType(), template) != null) {
                    throw new IllegalStateException("DUPLICATE_REGION_TEMPLATE_" + template.regionType());
                }
            }
            for (WorldChunkRegionType type : List.of(
                    WorldChunkRegionType.MEADOW,
                    WorldChunkRegionType.FOREST,
                    WorldChunkRegionType.POND)) {
                if (!templates.containsKey(type)) {
                    throw new IllegalStateException("MISSING_REGION_TEMPLATE_" + type);
                }
            }
            checksum = sha256(bytes);
        } catch (IOException exception) {
            throw new IllegalStateException("REGION_TEMPLATE_LOAD_FAILED", exception);
        }
    }

    public RegionTemplate require(WorldChunkRegionType type) {
        RegionTemplate template = templates.get(type);
        if (template == null) throw new IllegalArgumentException("REGION_TEMPLATE_NOT_FOUND_" + type);
        return template;
    }

    public RegionTemplate requireByKey(String templateKey) {
        return templates.values().stream()
                .filter(template -> template.templateKey().equals(templateKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("REGION_TEMPLATE_NOT_FOUND_" + templateKey));
    }

    public String checksum() {
        return checksum;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record RegistryFile(int generationVersion, List<RegionTemplate> templates) {
    }
}
