package com.projecteden.world.npc;

import com.projecteden.memorytaxonomy.legacy.LegacyRecognitionCompletedEvent;
import com.projecteden.photo.repository.PhotoRepository;
import com.projecteden.world.repository.WorldRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NpcPhotoQuestListener {
    private final PhotoRepository photos;
    private final NpcRelationshipService relationships;
    private final WorldRepository worlds;

    public NpcPhotoQuestListener(
            PhotoRepository photos,
            NpcRelationshipService relationships,
            WorldRepository worlds) {
        this.photos = photos;
        this.relationships = relationships;
        this.worlds = worlds;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRecognitionCompleted(LegacyRecognitionCompletedEvent event) {
        photos.findById(event.photoId()).ifPresent(photo -> {
            // Recognition also supports characters that have not entered Village yet.
            // Avoid invoking a transactional service that must roll back for WORLD_NOT_FOUND.
            if (!worlds.existsByCharacterId(photo.getCharacter().getId())) return;
            relationships.recordEvent(
                    photo.getCharacter().getUser().getId(),
                    "PHOTO:" + event.photoId(),
                    NpcQuestEventType.TAKE_PHOTO,
                    null);
        });
    }
}
