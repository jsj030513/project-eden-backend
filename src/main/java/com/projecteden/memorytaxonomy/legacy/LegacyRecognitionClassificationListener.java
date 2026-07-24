package com.projecteden.memorytaxonomy.legacy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class LegacyRecognitionClassificationListener {

	private static final Logger log =
			LoggerFactory.getLogger(LegacyRecognitionClassificationListener.class);

	private final LegacyMemoryClassificationWriter writer;

	public LegacyRecognitionClassificationListener(LegacyMemoryClassificationWriter writer) {
		this.writer = writer;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handle(LegacyRecognitionCompletedEvent event) {
		try {
			writer.writeFromLegacyRecognition(event.photoId(), event.recognitionId());
		} catch (Exception exception) {
			log.error(
					"Legacy classification dual-write failed. photoId={}, recognitionId={}",
					event.photoId(),
					event.recognitionId(),
					exception);
		}
	}
}
