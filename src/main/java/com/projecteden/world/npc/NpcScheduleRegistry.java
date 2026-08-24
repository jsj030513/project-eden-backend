package com.projecteden.world.npc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NpcScheduleRegistry {
    public static final int VERSION = 1;
    private final Map<String, ScheduleDefinition> schedules;

    public NpcScheduleRegistry() {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("world/npc-schedules-v1.yml")) {
            if (input == null) throw new IllegalStateException("NPC_SCHEDULE_RESOURCE_MISSING");
            RegistryFile file = new ObjectMapper(new YAMLFactory()).readValue(input, RegistryFile.class);
            if (file.version() != VERSION) throw new IllegalStateException("NPC_SCHEDULE_VERSION_UNSUPPORTED");
            schedules = Map.copyOf(file.schedules());
            validate();
        } catch (IOException exception) {
            throw new IllegalStateException("NPC_SCHEDULE_INVALID", exception);
        }
    }

    public ResolvedSchedule resolve(CanonicalNpcKey npcKey, LocalDateTime utcNow) {
        ScheduleDefinition schedule = require(npcKey);
        int minute = utcNow.getHour() * 60 + utcNow.getMinute();
        ScheduleSlot slot = schedule.slots().stream()
                .filter(candidate -> minute >= candidate.startMinute() && minute < candidate.endMinute())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("NPC_SCHEDULE_GAP"));
        NpcWorldAnchor destination = requireAnchor(slot.destination());
        return new ResolvedSchedule(
                schedule.scheduleKey(), slot.key(), slot.activity(), destination.tileX(),
                destination.tileY(), slot.dialogueKey(), slot.interactionEnabled());
    }

    public ScheduleDefinition require(CanonicalNpcKey key) {
        ScheduleDefinition value = schedules.get(key.name());
        if (value == null) throw new IllegalStateException("NPC_SCHEDULE_NOT_FOUND:" + key);
        return value;
    }

    public Map<String, ScheduleDefinition> schedules() { return schedules; }

    private void validate() {
        for (CanonicalNpcKey key : CanonicalNpcKey.values()) {
            ScheduleDefinition definition = require(key);
            if (!key.scheduleKey().equals(definition.scheduleKey()) || definition.slots() == null
                    || definition.slots().size() < 3) {
                throw new IllegalStateException("NPC_SCHEDULE_INCOMPLETE:" + key);
            }
            List<ScheduleSlot> ordered = definition.slots().stream()
                    .sorted(java.util.Comparator.comparingInt(ScheduleSlot::startMinute))
                    .toList();
            int cursor = 0;
            for (ScheduleSlot slot : ordered) {
                if (slot.startMinute() != cursor || slot.endMinute() <= slot.startMinute()
                        || slot.destination() == null || slot.activity() == null
                        || slot.dialogueKey() == null || slot.dialogueKey().isBlank()) {
                    throw new IllegalStateException("NPC_SCHEDULE_SLOT_INVALID:" + key);
                }
                requireAnchor(slot.destination());
                cursor = slot.endMinute();
            }
            if (cursor != 1440) throw new IllegalStateException("NPC_SCHEDULE_DAY_INCOMPLETE:" + key);
        }
    }

    private static NpcWorldAnchor requireAnchor(String name) {
        try {
            return NpcWorldAnchor.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalStateException("NPC_SCHEDULE_ANCHOR_INVALID:" + name, exception);
        }
    }

    public record RegistryFile(int version, Map<String, ScheduleDefinition> schedules) { }
    public record ScheduleDefinition(String scheduleKey, List<ScheduleSlot> slots) { }
    public record ScheduleSlot(
            String key,
            int startMinute,
            int endMinute,
            NpcActivity activity,
            String destination,
            String dialogueKey,
            boolean interactionEnabled) { }
    public record ResolvedSchedule(
            String scheduleKey,
            String slotKey,
            NpcActivity activity,
            int destinationX,
            int destinationY,
            String dialogueKey,
            boolean interactionEnabled) { }
}
