package com.projecteden.memorytaxonomy.observation.openai;

public interface OpenAIObservationClient {

	OpenAIObservationResponse observe(OpenAIObservationRequest request);
}
