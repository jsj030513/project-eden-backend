package com.projecteden.memorytaxonomy.observation.openai;

public class OpenAIObservationException extends RuntimeException {

	public OpenAIObservationException(String message) {
		super(message);
	}

	public OpenAIObservationException(String message, Throwable cause) {
		super(message, cause);
	}
}
