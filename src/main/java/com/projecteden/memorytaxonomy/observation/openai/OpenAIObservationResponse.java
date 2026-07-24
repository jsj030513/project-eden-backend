package com.projecteden.memorytaxonomy.observation.openai;

import java.math.BigDecimal;
import java.util.List;

public record OpenAIObservationResponse(
		Boolean recognized,
		BigDecimal confidence,
		List<String> subjects,
		List<String> objects,
		String scene,
		List<String> activities,
		List<String> relationships,
		List<String> moodSignals) {
}
