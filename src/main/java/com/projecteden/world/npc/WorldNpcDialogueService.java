package com.projecteden.world.npc;

import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.world.ecology.WorldPlayerPositionRepository;
import com.projecteden.world.repository.WorldRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorldNpcDialogueService {
    public static final Duration SESSION_TTL = Duration.ofMinutes(5);

    private final CharacterRepository characters;
    private final WorldRepository worlds;
    private final WorldPlayerPositionRepository positions;
    private final NpcRuntimeService runtime;
    private final NpcScheduleRegistry schedules;
    private final NpcDialogueRegistry registry;
    private final NpcDialogueSessionRepository sessions;
    private final NpcConversationStateRepository conversations;
    private final NpcRelationshipService relationships;
    private final Clock clock;

    public WorldNpcDialogueService(
            CharacterRepository characters,
            WorldRepository worlds,
            WorldPlayerPositionRepository positions,
            NpcRuntimeService runtime,
            NpcScheduleRegistry schedules,
            NpcDialogueRegistry registry,
            NpcDialogueSessionRepository sessions,
            NpcConversationStateRepository conversations,
            NpcRelationshipService relationships,
            Clock clock) {
        this.characters = characters;
        this.worlds = worlds;
        this.positions = positions;
        this.runtime = runtime;
        this.schedules = schedules;
        this.registry = registry;
        this.sessions = sessions;
        this.conversations = conversations;
        this.relationships = relationships;
        this.clock = clock;
    }

    @Transactional
    public DialogueSessionResponse start(Long userId, Long objectId) {
        var character = characters.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("CHARACTER_NOT_FOUND"));
        var world = worlds.findByCharacterIdForUpdate(character.getId())
                .orElseThrow(() -> new IllegalArgumentException("WORLD_NOT_FOUND"));
        runtime.ensureForWorld(world);
        NpcRuntimeState state = runtime.requireRuntime(world.getId(), objectId);
        LocalDateTime now = now();
        validateRange(character.getId(), state);
        NpcScheduleRegistry.ResolvedSchedule schedule = currentSchedule(state, now);
        if (!schedule.interactionEnabled()) throw new IllegalArgumentException("NPC_TALK_DISABLED");

        NpcDialogueSession existing = sessions
                .findFirstByCharacterIdAndNpcObjectIdAndCompletedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        character.getId(), objectId, now)
                .orElse(null);
        if (existing != null) return response(existing, state, null);

        NpcDialogueRegistry.DialogueDefinition definition = registry.require(schedule.dialogueKey());
        NpcDialogueSession created = sessions.save(NpcDialogueSession.create(
                world,
                state.getNpcObject(),
                character,
                schedule.dialogueKey(),
                definition.startNode(),
                now,
                now.plus(SESSION_TTL)));
        return response(created, state, null);
    }

    @Transactional
    public DialogueSessionResponse choose(
            Long userId,
            Long objectId,
            String sessionId,
            String choiceId) {
        var character = characters.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("CHARACTER_NOT_FOUND"));
        var world = worlds.findByCharacterIdForUpdate(character.getId())
                .orElseThrow(() -> new IllegalArgumentException("WORLD_NOT_FOUND"));
        NpcRuntimeState state = runtime.requireRuntime(world.getId(), objectId);
        NpcDialogueSession session = sessions.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("DIALOGUE_SESSION_NOT_FOUND"));
        if (!session.getCharacter().getId().equals(character.getId())
                || !session.getNpcObject().getId().equals(objectId)) {
            throw new IllegalArgumentException("DIALOGUE_SESSION_NOT_OWNED");
        }
        LocalDateTime now = now();
        if (now.isAfter(session.getExpiresAt())) throw new IllegalArgumentException("DIALOGUE_SESSION_EXPIRED");
        validateRange(character.getId(), state);
        if (session.isCompleted()) return response(session, state, null);

        NpcDialogueRegistry.DialogueNode current = registry.node(
                session.getDialogueKey(), session.getCurrentNodeId());
        NpcDialogueRegistry.DialogueChoice choice = choices(current).stream()
                .filter(candidate -> candidate.id().equals(choiceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("DIALOGUE_CHOICE_INVALID"));
        NpcDialogueRegistry.DialogueNode next = registry.node(
                session.getDialogueKey(), choice.nextNode());
        session.advance(choice.nextNode(), next.close(), now);
        NpcProgressResult progress = null;
        if (next.close() && !session.isCompletionRecorded()) {
            NpcConversationState conversation = conversations.findForUpdate(
                            character.getId(), objectId)
                    .orElseGet(() -> NpcConversationState.create(
                            world, state.getNpcObject(), character, now));
            conversation.complete(session.getDialogueKey(), now);
            conversations.save(conversation);
            session.recordCompletion(now);
            progress = relationships.completeDialogue(
                    character,
                    state,
                    session.getId(),
                    session.getDialogueKey(),
                    choice.id(),
                    now);
        }
        return response(session, state, progress);
    }

    @Transactional
    public void close(Long userId, Long objectId, String sessionId) {
        var character = characters.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("CHARACTER_NOT_FOUND"));
        var world = worlds.findByCharacterIdForUpdate(character.getId())
                .orElseThrow(() -> new IllegalArgumentException("WORLD_NOT_FOUND"));
        NpcDialogueSession session = sessions.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("DIALOGUE_SESSION_NOT_FOUND"));
        if (!session.getCharacter().getId().equals(character.getId())
                || !session.getNpcObject().getId().equals(objectId)
                || !session.getWorld().getId().equals(world.getId())) {
            throw new IllegalArgumentException("DIALOGUE_SESSION_NOT_OWNED");
        }
        if (!session.isCompleted()) session.close(now());
    }

    private DialogueSessionResponse response(
            NpcDialogueSession session,
            NpcRuntimeState state,
            NpcProgressResult progress) {
        NpcDialogueRegistry.DialogueNode node = registry.node(
                session.getDialogueKey(), session.getCurrentNodeId());
        long count = conversations.findByCharacterIdAndNpcObjectId(
                        session.getCharacter().getId(), session.getNpcObject().getId())
                .map(NpcConversationState::getConversationCount)
                .orElse(0L);
        return new DialogueSessionResponse(
                session.getId(),
                runtime.projection(state),
                new DialogueNodeResponse(
                        session.getCurrentNodeId(),
                        node.speaker(),
                        node.text(),
                        choices(node).stream()
                                .map(choice -> new DialogueChoiceResponse(choice.id(), choice.label()))
                                .toList(),
                        node.close()),
                session.isCompleted(),
                count,
                session.getExpiresAt(),
                progress == null
                        ? relationships.relationship(
                                session.getCharacter().getUser().getId(),
                                session.getNpcObject().getId())
                        : progress.relationship(),
                progress == null ? List.of() : progress.notifications());
    }

    private void validateRange(Long characterId, NpcRuntimeState state) {
        var player = positions.findByCharacterId(characterId)
                .orElseThrow(() -> new IllegalArgumentException("PLAYER_POSITION_NOT_FOUND"));
        int distance = Math.abs(player.getX() - state.getTileX())
                + Math.abs(player.getY() - state.getTileY());
        if (!state.getNpcKey().enabled()
                || distance != state.getNpcKey().interactionRange()) {
            throw new IllegalArgumentException("NPC_OUT_OF_RANGE");
        }
    }

    private NpcScheduleRegistry.ResolvedSchedule currentSchedule(
            NpcRuntimeState state,
            LocalDateTime now) {
        return schedules.resolve(state.getNpcKey(), now);
    }

    private static List<NpcDialogueRegistry.DialogueChoice> choices(
            NpcDialogueRegistry.DialogueNode node) {
        return node.choices() == null ? List.of() : node.choices();
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

}
