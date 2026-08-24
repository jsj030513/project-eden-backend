package com.projecteden.world.npc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class NpcQuestRegistry {
    public static final int VERSION = 1;
    private final List<QuestDefinition> quests;
    private final Map<String, QuestDefinition> byId;

    public NpcQuestRegistry() {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("world/npc-quests-v1.yml")) {
            if (input == null) throw new IllegalStateException("NPC_QUEST_RESOURCE_MISSING");
            RegistryFile file = new ObjectMapper(new YAMLFactory()).readValue(input, RegistryFile.class);
            if (file.version() != VERSION) throw new IllegalStateException("NPC_QUEST_VERSION_UNSUPPORTED");
            quests = List.copyOf(file.quests());
            Map<String, QuestDefinition> definitions = new HashMap<>();
            for (QuestDefinition quest : quests) {
                if (definitions.put(quest.id(), quest) != null) {
                    throw new IllegalStateException("NPC_QUEST_DUPLICATE:" + quest.id());
                }
            }
            byId = Map.copyOf(definitions);
            validate();
        } catch (IOException exception) {
            throw new IllegalStateException("NPC_QUEST_INVALID", exception);
        }
    }

    public List<QuestDefinition> all() {
        return quests;
    }

    public QuestDefinition require(String questId) {
        QuestDefinition quest = byId.get(questId);
        if (quest == null) throw new IllegalArgumentException("NPC_QUEST_NOT_FOUND");
        return quest;
    }

    private void validate() {
        Set<String> supported = Set.of(
                "TALK", "VISIT_LOCATION", "TAKE_PHOTO", "INSPECT",
                "ANIMAL_INTERACTION", "COMMUNITY_VISIT");
        Set<String> ids = new HashSet<>(byId.keySet());
        for (QuestDefinition quest : quests) {
            if (quest.version() != VERSION
                    || quest.id() == null
                    || quest.npcKey() == null
                    || quest.title() == null
                    || quest.requirements() == null
                    || quest.requirements().targetCount() <= 0
                    || !supported.contains(quest.requirements().eventType())
                    || quest.requirements().minAffinity() < 0
                    || quest.requirements().minAffinity() > NpcAffinityState.MAX_AFFINITY
                    || quest.rewards() == null
                    || quest.rewards().affinity() < 0) {
                throw new IllegalStateException("NPC_QUEST_INVALID:" + quest.id());
            }
            CanonicalNpcKey.valueOf(quest.npcKey());
            if (quest.nextQuest() != null && !ids.contains(quest.nextQuest())) {
                throw new IllegalStateException("NPC_QUEST_NEXT_INVALID:" + quest.id());
            }
            if (quest.requirements().completedQuest() != null
                    && !ids.contains(quest.requirements().completedQuest())) {
                throw new IllegalStateException("NPC_QUEST_REQUIREMENT_INVALID:" + quest.id());
            }
        }
    }

    public record RegistryFile(int version, List<QuestDefinition> quests) { }
    public record QuestDefinition(
            String id,
            String npcKey,
            String title,
            String description,
            QuestRequirement requirements,
            QuestReward rewards,
            String nextQuest,
            boolean repeatable,
            boolean hidden,
            int version) { }
    public record QuestRequirement(
            String eventType,
            String target,
            int targetCount,
            int minAffinity,
            String completedQuest) { }
    public record QuestReward(int affinity) { }
}
