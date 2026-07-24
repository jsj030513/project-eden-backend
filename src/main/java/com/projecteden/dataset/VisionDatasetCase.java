package com.projecteden.dataset;
public record VisionDatasetCase(String schemaVersion, VisionDatasetCaseId caseId, VisionDatasetId datasetId, String relativePath, String contentType, int width, int height, String sha256, VisionConsentMetadata consent, VisionGroundTruth groundTruth, String reviewStatus, String status) { }
