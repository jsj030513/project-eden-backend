package com.projecteden.dataset;
public record VisionDataset(String schemaVersion, VisionDatasetId id, String name, int version, String status, int caseCount) { public VisionDataset { schemaVersion = schemaVersion == null ? "eden-dataset-schema-v1" : schemaVersion; status = status == null ? "ACTIVE" : status; } }
