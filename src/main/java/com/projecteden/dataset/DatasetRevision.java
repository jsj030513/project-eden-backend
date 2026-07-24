package com.projecteden.dataset;

import java.time.Instant;

public record DatasetRevision(
		String schemaVersion,
		String revisionId,
		VisionDatasetId datasetId,
		Instant createdAt,
		RevisionMetadata metadata,
		int caseCount,
		String manifestChecksum,
		String datasetChecksum,
		String summaryChecksum,
		RevisionStatus status) {

	public DatasetRevision {
		schemaVersion = schemaVersion == null ? "eden-dataset-revision-schema-v1" : schemaVersion;
		if (revisionId == null || !revisionId.matches("rev-[0-9]{6}")) throw new IllegalArgumentException("INVALID_REVISION_ID");
		if (datasetId == null || createdAt == null || metadata == null || caseCount < 0 || manifestChecksum == null || datasetChecksum == null || summaryChecksum == null || status == null) {
			throw new IllegalArgumentException("INVALID_DATASET_REVISION");
		}
	}
}
