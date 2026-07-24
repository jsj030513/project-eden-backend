package com.projecteden.memorytaxonomy.observation.openai;

public record OpenAIObservationRequest(
		String model,
		String imageDataUrl,
		String imageDetail) {

	@Override
	public String toString() {
		return "OpenAIObservationRequest{" +
				"model='" + model + '\'' +
				", imageDataUrlPresent=" + (imageDataUrl != null && !imageDataUrl.isBlank()) +
				", imageDetail='" + imageDetail + '\'' +
				'}';
	}
}
