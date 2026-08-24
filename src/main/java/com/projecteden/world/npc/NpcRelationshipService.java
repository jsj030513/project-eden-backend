package com.projecteden.world.npc;

import com.projecteden.character.domain.Character;
import com.projecteden.character.repository.CharacterRepository;
import com.projecteden.world.ecology.WorldPlacedObject;
import com.projecteden.world.domain.World;
import com.projecteden.world.repository.WorldRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NpcRelationshipService {
    static final int REPLAY_BATCH_SIZE = 100;
    private static final int BASE_DIALOGUE_AFFINITY = 10;
    private static final int FIRST_DIALOGUE_BONUS = 20;
    private static final int DAILY_FIRST_BONUS = 10;
    private static final int REPEATED_DIALOGUE_AFFINITY = 3;
    private static final Duration RAPID_REPEAT_WINDOW = Duration.ofMinutes(5);

    private final CharacterRepository characters;
    private final WorldRepository worlds;
    private final NpcRuntimeService runtime;
    private final NpcRuntimeStateRepository runtimeStates;
    private final NpcAffinityStateRepository affinities;
    private final NpcAffinityEventRepository affinityEvents;
    private final NpcQuestStateRepository questStates;
    private final NpcQuestEventRepository questEvents;
    private final NpcQuestRegistry quests;
    private final Clock clock;

    public NpcRelationshipService(
            CharacterRepository characters,
            WorldRepository worlds,
            NpcRuntimeService runtime,
            NpcRuntimeStateRepository runtimeStates,
            NpcAffinityStateRepository affinities,
            NpcAffinityEventRepository affinityEvents,
            NpcQuestStateRepository questStates,
            NpcQuestEventRepository questEvents,
            NpcQuestRegistry quests,
            Clock clock) {
        this.characters = characters;
        this.worlds = worlds;
        this.runtime = runtime;
        this.runtimeStates = runtimeStates;
        this.affinities = affinities;
        this.affinityEvents = affinityEvents;
        this.questStates = questStates;
        this.questEvents = questEvents;
        this.quests = quests;
        this.clock = clock;
    }

    @Transactional
    public List<NpcRelationshipResponse> relationships(Long userId) {
        Character character = requireCharacter(userId);
        var world = worlds.findByCharacterIdForUpdate(character.getId())
                .orElseThrow(() -> new IllegalArgumentException("WORLD_NOT_FOUND"));
        runtime.ensureForWorld(world);
        return snapshots(character, world,
                runtimeStates.findByWorldIdOrderByNpcObjectIdAsc(world.getId()), now());
    }

    @Transactional
    public NpcRelationshipResponse relationship(Long userId, Long npcObjectId) {
        Character character = requireCharacter(userId);
        var world = worlds.findByCharacterIdForUpdate(character.getId())
                .orElseThrow(() -> new IllegalArgumentException("WORLD_NOT_FOUND"));
        runtime.ensureForWorld(world);
        runtime.requireRuntime(world.getId(), npcObjectId);
        return snapshots(
                        character,
                        world,
                        runtimeStates.findByWorldIdOrderByNpcObjectIdAsc(world.getId()),
                        now())
                .stream()
                .filter(response -> response.npcObjectId().equals(npcObjectId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("NPC_NOT_OWNED"));
    }

    @Transactional
    public NpcProgressResult completeDialogue(
            Character character,
            NpcRuntimeState npc,
            String sessionId,
            String dialogueKey,
            String choiceId,
            LocalDateTime now) {
        String eventKey = "DIALOGUE:" + sessionId;
        NpcAffinityState affinity = requireAffinity(character, npc.getNpcObject(), now);
        if (affinityEvents.existsByCharacterIdAndNpcObjectIdAndEventKey(
                character.getId(), npc.getNpcObject().getId(), eventKey)) {
            return new NpcProgressResult(
                    snapshot(character, npc, affinity, stateMap(character.getId())),
                    List.of());
        }

        int amount = dialogueAffinity(character.getId(), npc.getNpcObject().getId(), affinity, dialogueKey, now);
        NpcAffinityState.AffinityChange change = affinity.completeConversation(amount, now);
        affinities.save(affinity);
        affinityEvents.save(NpcAffinityEvent.create(
                character, npc.getNpcObject(), eventKey, dialogueKey, choiceId, change.amount(), now));

        List<NpcProgressNotification> notifications = new ArrayList<>();
        addAffinityNotifications(notifications, change);
        recordQuestEvent(
                character,
                eventKey,
                NpcQuestEventType.TALK,
                npc.getNpcKey().name(),
                now,
                notifications);
        return new NpcProgressResult(
                snapshot(character, npc, affinity, stateMap(character.getId())),
                List.copyOf(notifications));
    }

    @Transactional
    public List<NpcProgressNotification> recordEvent(
            Long userId,
            String eventKey,
            NpcQuestEventType eventType,
            String target) {
        Character character = requireCharacter(userId);
        return recordQuestEvent(character, eventKey, eventType, target, now(), new ArrayList<>());
    }

    private List<NpcProgressNotification> recordQuestEvent(
            Character character,
            String eventKey,
            NpcQuestEventType eventType,
            String target,
            LocalDateTime now,
            List<NpcProgressNotification> notifications) {
        if (questEvents.existsByCharacterIdAndEventKey(character.getId(), eventKey)) {
            return List.copyOf(notifications);
        }
        World world = worlds.findByCharacterIdForUpdate(character.getId())
                .orElseThrow(() -> new IllegalArgumentException("WORLD_NOT_FOUND"));
        if (questEvents.existsByCharacterIdAndEventKey(character.getId(), eventKey)) {
            return List.copyOf(notifications);
        }
        Set<String> activeAtOccurrence = activeQuestIds(
                character.getId(), eventType, target);
        NpcQuestEvent event = questEvents.save(NpcQuestEvent.create(
                character, world, eventKey, eventType, target, activeAtOccurrence, now));
        runtime.ensureForWorld(world);
        List<NpcRuntimeState> npcs = runtimeStates.findByWorldIdOrderByNpcObjectIdAsc(world.getId());
        if (npcs.isEmpty()) {
            if (activeAtOccurrence.isEmpty()) {
                event.ignored(now, "NO_ACTIVE_QUEST_AT_OCCURRENCE");
            } else {
                event.pending("WAITING_FOR_NPC_RUNTIME");
            }
            return List.copyOf(notifications);
        }
        ensureStates(character, npcs, now, notifications);
        replayPendingInternal(character, world, npcs, now, event.getId());
        processEvent(event, character, npcs, now, notifications, false);
        ensureStates(character, npcs, now, notifications);
        return List.copyOf(notifications);
    }

    private List<NpcRelationshipResponse> snapshots(
            Character character,
            World world,
            List<NpcRuntimeState> npcs,
            LocalDateTime now) {
        ensureStates(character, npcs, now, new ArrayList<>());
        ReplayResult replay = replayPendingInternal(character, world, npcs, now, null);
        if (replay.processed() > 0) {
            ensureStates(character, npcs, now, new ArrayList<>());
        }
        Map<Long, NpcAffinityState> affinityByObject = new HashMap<>();
        for (NpcAffinityState state : affinities.findAllForCharacter(character.getId())) {
            affinityByObject.put(state.getNpcObject().getId(), state);
        }
        Map<String, NpcQuestState> states = stateMap(character.getId());
        return npcs.stream()
                .map(npc -> snapshot(
                        character,
                        npc,
                        affinityByObject.get(npc.getNpcObject().getId()),
                        states))
                .toList();
    }

    @Transactional
    public ReplayResult replayPending(Long userId) {
        Character character = requireCharacter(userId);
        World world = worlds.findByCharacterIdForUpdate(character.getId())
                .orElseThrow(() -> new IllegalArgumentException("WORLD_NOT_FOUND"));
        runtime.ensureForWorld(world);
        List<NpcRuntimeState> npcs = runtimeStates.findByWorldIdOrderByNpcObjectIdAsc(world.getId());
        ensureStates(character, npcs, now(), new ArrayList<>());
        return replayPendingInternal(character, world, npcs, now(), null);
    }

    private ReplayResult replayPendingInternal(
            Character character,
            World world,
            List<NpcRuntimeState> npcs,
            LocalDateTime now,
            Long excludedEventId) {
        if (npcs.isEmpty()) return new ReplayResult(0, 0, 0, 0);
        List<NpcQuestEvent> batch = questEvents.findReplayBatchForUpdate(
                character.getId(),
                world.getId(),
                List.of(NpcQuestEventProcessingStatus.PENDING, NpcQuestEventProcessingStatus.FAILED),
                PageRequest.of(0, REPLAY_BATCH_SIZE));
        int processed = 0;
        int ignored = 0;
        int failed = 0;
        for (NpcQuestEvent event : batch) {
            if (excludedEventId != null && excludedEventId.equals(event.getId())) continue;
            try {
                NpcQuestEventProcessingStatus outcome = processEvent(
                        event, character, npcs, now, new ArrayList<>(), true);
                if (outcome == NpcQuestEventProcessingStatus.PROCESSED) processed++;
                else if (outcome == NpcQuestEventProcessingStatus.IGNORED) ignored++;
            } catch (RuntimeException exception) {
                event.failed(now, safeFailureReason(exception));
                failed++;
            }
        }
        return new ReplayResult(batch.size(), processed, ignored, failed);
    }

    private NpcQuestEventProcessingStatus processEvent(
            NpcQuestEvent event,
            Character character,
            List<NpcRuntimeState> npcs,
            LocalDateTime now,
            List<NpcProgressNotification> notifications,
            boolean replay) {
        Map<String, NpcQuestState> states = stateMap(character.getId());
        Map<String, NpcRuntimeState> npcByKey = npcMap(npcs);
        boolean progressed = false;
        Set<String> eligible = event.eligibleQuestIds();
        for (NpcQuestRegistry.QuestDefinition definition : quests.all()) {
            if (!npcByKey.containsKey(definition.npcKey())) continue;
            NpcQuestRegistry.QuestRequirement requirement = definition.requirements();
            if (!requirement.eventType().equals(event.getEventType().name())
                    || !matchesTarget(requirement.target(), event.getTarget())) continue;
            NpcQuestState state = states.get(definition.id());
            if (state == null) continue;
            if (replay && (!eligible.contains(definition.id())
                    || state.getStatus() != NpcQuestStatus.ACTIVE)) continue;
            if (!replay && state.getStatus() == NpcQuestStatus.LOCKED) continue;
            if (state.getStatus() == NpcQuestStatus.COMPLETED) {
                if (replay || !definition.repeatable()) continue;
                state.resetRepeatable(now);
            }
            int before = state.getProgress();
            boolean completed = state.progress(requirement.targetCount(), now);
            if (state.getProgress() == before && !completed) continue;
            progressed = true;
            if (!completed) continue;

            NpcRuntimeState rewardNpc = npcByKey.get(definition.npcKey());
            NpcAffinityState affinity = requireAffinity(character, rewardNpc.getNpcObject(), now);
            NpcAffinityState.AffinityChange reward = affinity.completeQuest(
                    definition.rewards().affinity(), now);
            affinities.save(affinity);
            state.markRewardClaimed(now);
            if (!replay) {
                notifications.add(new NpcProgressNotification(
                        "QUEST_COMPLETED", "퀘스트 완료 · " + definition.title(), 0));
                addAffinityNotifications(notifications, reward);
            }
        }
        questStates.saveAll(states.values());
        if (progressed) {
            event.processed(now, replay ? "REPLAY_APPLIED" : "IMMEDIATE_APPLIED");
            return NpcQuestEventProcessingStatus.PROCESSED;
        }
        event.ignored(now, replay ? "NO_ACTIVE_ELIGIBLE_QUEST" : "NO_APPLICABLE_QUEST");
        return NpcQuestEventProcessingStatus.IGNORED;
    }

    private Set<String> activeQuestIds(
            Long characterId,
            NpcQuestEventType eventType,
            String target) {
        Map<String, NpcQuestState> states = stateMap(characterId);
        Set<String> result = new LinkedHashSet<>();
        for (NpcQuestRegistry.QuestDefinition definition : quests.all()) {
            NpcQuestState state = states.get(definition.id());
            if (state == null || state.getStatus() != NpcQuestStatus.ACTIVE) continue;
            NpcQuestRegistry.QuestRequirement requirement = definition.requirements();
            if (requirement.eventType().equals(eventType.name())
                    && matchesTarget(requirement.target(), target)) {
                result.add(definition.id());
            }
        }
        return Set.copyOf(result);
    }

    private static String safeFailureReason(RuntimeException exception) {
        return exception instanceof IllegalArgumentException
                ? "INVALID_EVENT_DATA"
                : "PROCESSING_FAILED";
    }

    public record ReplayResult(int selected, int processed, int ignored, int failed) { }

    private NpcRelationshipResponse snapshot(
            Character character,
            NpcRuntimeState npc,
            NpcAffinityState affinity,
            Map<String, NpcQuestState> stateMap) {
        if (affinity == null) {
            throw new IllegalStateException("NPC_AFFINITY_NOT_INITIALIZED");
        }
        List<NpcQuestResponse> questResponses = quests.all().stream()
                .filter(definition -> definition.npcKey().equals(npc.getNpcKey().name()))
                .map(definition -> response(definition, stateMap.get(definition.id())))
                .filter(response -> !response.hidden()
                        || response.status() == NpcQuestStatus.ACTIVE
                        || response.status() == NpcQuestStatus.COMPLETED)
                .toList();
        return new NpcRelationshipResponse(
                npc.getNpcObject().getId(),
                npc.getNpcKey().name(),
                affinity.getCurrentAffinity(),
                affinity.getMaxAffinity(),
                affinity.getLevel(),
                relationshipLabel(affinity.getLevel()),
                affinity.getLastInteractionAt(),
                affinity.getConversationCount(),
                affinity.getQuestCompletedCount(),
                questResponses);
    }

    private void ensureStates(
            Character character,
            List<NpcRuntimeState> npcs,
            LocalDateTime now,
            List<NpcProgressNotification> notifications) {
        Map<String, NpcRuntimeState> npcByKey = npcMap(npcs);
        Map<Long, NpcAffinityState> affinityByObject = new HashMap<>();
        for (NpcAffinityState state : affinities.findAllForCharacter(character.getId())) {
            affinityByObject.put(state.getNpcObject().getId(), state);
        }
        List<NpcAffinityState> newAffinities = new ArrayList<>();
        for (NpcRuntimeState npc : npcs) {
            if (!affinityByObject.containsKey(npc.getNpcObject().getId())) {
                NpcAffinityState created = NpcAffinityState.create(character, npc.getNpcObject(), now);
                affinityByObject.put(npc.getNpcObject().getId(), created);
                newAffinities.add(created);
            }
        }
        if (!newAffinities.isEmpty()) affinities.saveAll(newAffinities);

        Map<String, NpcQuestState> states = stateMap(character.getId());
        for (NpcQuestRegistry.QuestDefinition definition : quests.all()) {
            NpcRuntimeState npc = npcByKey.get(definition.npcKey());
            if (npc == null) continue;
            NpcAffinityState affinity = affinityByObject.get(npc.getNpcObject().getId());
            boolean unlocked = unlocked(definition, affinity, states);
            NpcQuestState state = states.get(definition.id());
            if (state == null) {
                state = NpcQuestState.create(
                        character,
                        definition.id(),
                        unlocked ? NpcQuestStatus.AVAILABLE : NpcQuestStatus.LOCKED,
                        now);
                states.put(definition.id(), state);
                if (unlocked && !definition.hidden()) {
                    notifications.add(new NpcProgressNotification(
                            "QUEST_AVAILABLE", "새 퀘스트 · " + definition.title(), 0));
                }
            } else if (unlocked && state.getStatus() == NpcQuestStatus.LOCKED) {
                state.unlock(now);
                if (!definition.hidden()) {
                    notifications.add(new NpcProgressNotification(
                            "QUEST_AVAILABLE", "새 퀘스트 · " + definition.title(), 0));
                }
            }
        }
        questStates.saveAll(states.values());
    }

    private boolean unlocked(
            NpcQuestRegistry.QuestDefinition definition,
            NpcAffinityState affinity,
            Map<String, NpcQuestState> states) {
        if (affinity.getCurrentAffinity() < definition.requirements().minAffinity()) return false;
        String prerequisite = definition.requirements().completedQuest();
        return prerequisite == null
                || (states.containsKey(prerequisite)
                && states.get(prerequisite).getStatus() == NpcQuestStatus.COMPLETED);
    }

    private NpcAffinityState requireAffinity(
            Character character,
            WorldPlacedObject npcObject,
            LocalDateTime now) {
        return affinities.findForUpdate(character.getId(), npcObject.getId())
                .orElseGet(() -> affinities.save(NpcAffinityState.create(character, npcObject, now)));
    }

    private int dialogueAffinity(
            Long characterId,
            Long objectId,
            NpcAffinityState affinity,
            String dialogueKey,
            LocalDateTime now) {
        if (affinity.getLastInteractionAt() != null
                && Duration.between(affinity.getLastInteractionAt(), now).compareTo(RAPID_REPEAT_WINDOW) < 0) {
            return 0;
        }
        long repeats = affinityEvents.countByCharacterIdAndNpcObjectIdAndDialogueKey(
                characterId, objectId, dialogueKey);
        int amount = repeats == 0 ? BASE_DIALOGUE_AFFINITY : REPEATED_DIALOGUE_AFFINITY;
        if (affinity.getConversationCount() == 0) amount += FIRST_DIALOGUE_BONUS;
        LocalDate date = now.toLocalDate();
        boolean alreadyTalkedToday = affinityEvents
                .existsByCharacterIdAndNpcObjectIdAndCreatedAtBetween(
                        characterId, objectId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        if (!alreadyTalkedToday) amount += DAILY_FIRST_BONUS;
        return amount;
    }

    private static boolean matchesTarget(String expected, String actual) {
        return expected == null || expected.isBlank() || expected.equals(actual);
    }

    private Map<String, NpcQuestState> stateMap(Long characterId) {
        Map<String, NpcQuestState> result = new HashMap<>();
        for (NpcQuestState state : questStates.findByCharacterIdOrderByQuestIdAsc(characterId)) {
            result.put(state.getQuestId(), state);
        }
        return result;
    }

    private static Map<String, NpcRuntimeState> npcMap(List<NpcRuntimeState> npcs) {
        Map<String, NpcRuntimeState> result = new HashMap<>();
        for (NpcRuntimeState npc : npcs) result.put(npc.getNpcKey().name(), npc);
        return result;
    }

    private static NpcQuestResponse response(
            NpcQuestRegistry.QuestDefinition definition,
            NpcQuestState state) {
        return new NpcQuestResponse(
                definition.id(),
                definition.npcKey(),
                definition.title(),
                definition.description(),
                state.getStatus(),
                state.getProgress(),
                definition.requirements().targetCount(),
                definition.repeatable(),
                definition.hidden(),
                state.getStartedAt(),
                state.getCompletedAt());
    }

    private static void addAffinityNotifications(
            List<NpcProgressNotification> notifications,
            NpcAffinityState.AffinityChange change) {
        if (change.amount() > 0) {
            notifications.add(new NpcProgressNotification(
                    "AFFINITY_INCREASED", "호감도가 " + change.amount() + " 올랐어요.", change.amount()));
        }
        if (change.previousLevel() != change.level()) {
            notifications.add(new NpcProgressNotification(
                    "RELATIONSHIP_CHANGED",
                    "관계가 " + relationshipLabel(change.level()) + " 단계가 되었어요.",
                    0));
        }
    }

    private static String relationshipLabel(AffinityLevel level) {
        return switch (level) {
            case STRANGER -> "낯선 사이";
            case ACQUAINTANCE -> "아는 사이";
            case FRIEND -> "친구";
            case CLOSE_FRIEND -> "가까운 친구";
            case BEST_FRIEND -> "가장 친한 친구";
        };
    }

    private Character requireCharacter(Long userId) {
        return characters.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("CHARACTER_NOT_FOUND"));
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
