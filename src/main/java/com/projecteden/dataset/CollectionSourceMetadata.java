package com.projecteden.dataset;

import java.time.Instant;

public record CollectionSourceMetadata(CollectionSourceType sourceType, Instant collectedAt, String collectorId,
		CollectionConsentStatus consentStatus, CollectionLicenseType licenseType, String originalFilename, String notes) { }
