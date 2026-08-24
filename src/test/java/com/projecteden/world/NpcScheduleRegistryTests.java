package com.projecteden.world;

import static org.assertj.core.api.Assertions.assertThat;

import com.projecteden.world.npc.CanonicalNpcKey;
import com.projecteden.world.npc.NpcActivity;
import com.projecteden.world.npc.NpcDialogueRegistry;
import com.projecteden.world.npc.NpcScheduleRegistry;
import com.projecteden.world.npc.NpcWorldAnchor;
import com.projecteden.world.ecology.WorldHubLayout;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class NpcScheduleRegistryTests {
    private final NpcScheduleRegistry schedules = new NpcScheduleRegistry();
    private final NpcDialogueRegistry dialogues = new NpcDialogueRegistry();

    @Test
    void loadsEveryCanonicalScheduleWithACompleteNonOverlappingDay() {
        assertThat(schedules.schedules()).hasSize(CanonicalNpcKey.values().length);
        for (CanonicalNpcKey npc : CanonicalNpcKey.values()) {
            var slots = schedules.require(npc).slots();
            assertThat(npc.homeAnchor()).isNotNull();
            assertThat(npc.interactionRange()).isEqualTo(1);
            assertThat(npc.enabled()).isTrue();
            assertThat(slots).hasSizeGreaterThanOrEqualTo(3);
            assertThat(slots.getFirst().startMinute()).isZero();
            assertThat(slots.getLast().endMinute()).isEqualTo(1440);
            for (int index = 1; index < slots.size(); index++) {
                assertThat(slots.get(index).startMinute())
                        .isEqualTo(slots.get(index - 1).endMinute());
            }
        }
    }

    @Test
    void resolvesFixedUtcClockAtSlotBoundariesDeterministically() {
        var before = schedules.resolve(
                CanonicalNpcKey.NPC_MAYOR,
                LocalDateTime.of(2026, 7, 29, 7, 59));
        var boundary = schedules.resolve(
                CanonicalNpcKey.NPC_MAYOR,
                LocalDateTime.of(2026, 7, 29, 8, 0));
        var repeated = schedules.resolve(
                CanonicalNpcKey.NPC_MAYOR,
                LocalDateTime.of(2026, 7, 29, 8, 0));

        assertThat(before.activity()).isEqualTo(NpcActivity.RESTING);
        assertThat(boundary.activity()).isEqualTo(NpcActivity.WORKING);
        assertThat(boundary).isEqualTo(repeated);
    }

    @Test
    void loadsBranchingDialogueForEveryCanonicalNpcWithoutDeadEnds() {
        for (CanonicalNpcKey npc : CanonicalNpcKey.values()) {
            var dialogue = dialogues.require(npc.defaultDialogueKey());
            assertThat(dialogue.nodes()).hasSizeGreaterThanOrEqualTo(3);
            assertThat(dialogue.nodes().get(dialogue.startNode()).choices()).hasSizeGreaterThanOrEqualTo(1);
            assertThat(dialogue.nodes().values()).anyMatch(NpcDialogueRegistry.DialogueNode::close);
        }
    }

    @Test
    void canonicalScheduleDestinationsAvoidEveryProtectedHubAccessTile() {
        for (var schedule : schedules.schedules().values()) {
            for (var slot : schedule.slots()) {
                NpcWorldAnchor destination = NpcWorldAnchor.valueOf(slot.destination());
                assertThat(WorldHubLayout.isNpcProtectedTile(
                        destination.tileX(), destination.tileY()))
                        .as("schedule destination %s", destination)
                        .isFalse();
            }
        }
    }
}
