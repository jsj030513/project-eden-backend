package com.projecteden.world.npc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class NpcDialogueRegistry {
    public static final int VERSION = 1;
    private final Map<String, DialogueDefinition> dialogues;

    public NpcDialogueRegistry() {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("world/npc-dialogues-v1.yml")) {
            if (input == null) throw new IllegalStateException("NPC_DIALOGUE_RESOURCE_MISSING");
            RegistryFile file = new ObjectMapper(new YAMLFactory()).readValue(input, RegistryFile.class);
            if (file.version() != VERSION) throw new IllegalStateException("NPC_DIALOGUE_VERSION_UNSUPPORTED");
            dialogues = Map.copyOf(file.dialogues());
            validate();
        } catch (IOException exception) {
            throw new IllegalStateException("NPC_DIALOGUE_INVALID", exception);
        }
    }

    public DialogueDefinition require(String dialogueKey) {
        DialogueDefinition dialogue = dialogues.get(dialogueKey);
        if (dialogue == null) throw new IllegalArgumentException("DIALOGUE_NOT_FOUND");
        return dialogue;
    }

    public DialogueNode node(String dialogueKey, String nodeId) {
        DialogueNode node = require(dialogueKey).nodes().get(nodeId);
        if (node == null) throw new IllegalArgumentException("DIALOGUE_NODE_INVALID");
        return node;
    }

    private void validate() {
        for (CanonicalNpcKey npc : CanonicalNpcKey.values()) {
            DialogueDefinition dialogue = require(npc.defaultDialogueKey());
            if (dialogue.nodes() == null || dialogue.nodes().size() < 3
                    || !dialogue.nodes().containsKey(dialogue.startNode())) {
                throw new IllegalStateException("NPC_DIALOGUE_INCOMPLETE:" + npc);
            }
            for (Map.Entry<String, DialogueNode> entry : dialogue.nodes().entrySet()) {
                DialogueNode node = entry.getValue();
                List<DialogueChoice> choices = node.choices() == null ? List.of() : node.choices();
                if (!node.close() && choices.isEmpty()) {
                    throw new IllegalStateException("NPC_DIALOGUE_DEAD_END:" + entry.getKey());
                }
                Set<String> ids = new HashSet<>();
                for (DialogueChoice choice : choices) {
                    if (!ids.add(choice.id()) || !dialogue.nodes().containsKey(choice.nextNode())) {
                        throw new IllegalStateException("NPC_DIALOGUE_CHOICE_INVALID:" + entry.getKey());
                    }
                }
            }
        }
    }

    public record RegistryFile(int version, Map<String, DialogueDefinition> dialogues) { }
    public record DialogueDefinition(String startNode, Map<String, DialogueNode> nodes) { }
    public record DialogueNode(
            String speaker,
            String text,
            List<DialogueChoice> choices,
            String nextNode,
            boolean close) { }
    public record DialogueChoice(String id, String label, String nextNode) { }
}
