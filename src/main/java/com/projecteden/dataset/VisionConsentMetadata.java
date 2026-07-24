package com.projecteden.dataset;

import java.time.Instant;

public record VisionConsentMetadata(boolean storageAllowed, boolean evaluationAllowed, boolean trainingAllowed, boolean redistributionAllowed, Instant consentRecordedAt, String consentVersion) {
	public boolean importAllowed() { return storageAllowed && evaluationAllowed; }
}
