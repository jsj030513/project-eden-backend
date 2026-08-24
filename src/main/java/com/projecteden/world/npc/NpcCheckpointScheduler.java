package com.projecteden.world.npc;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class NpcCheckpointScheduler {
    private static final int MAX_WORLDS_PER_TICK = 100;
    private final NpcRuntimeStateRepository states;
    private final NpcRuntimeService runtime;
    private final Clock clock;

    public NpcCheckpointScheduler(
            NpcRuntimeStateRepository states,
            NpcRuntimeService runtime,
            Clock clock) {
        this.states = states;
        this.runtime = runtime;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${eden.world.npc.checkpoint-ms:5000}")
    public void checkpoint() {
        LocalDateTime cutoff = LocalDateTime.ofInstant(
                clock.instant().minus(NpcRuntimeService.CHECKPOINT_CADENCE), ZoneOffset.UTC);
        states.findDueWorldIds(cutoff, PageRequest.of(0, MAX_WORLDS_PER_TICK)).stream()
                .forEach(worldId -> {
                    try {
                        runtime.checkpointWorld(worldId);
                    } catch (RuntimeException ignored) {
                        // One corrupt world must not stop the scheduler. The next checkpoint retries it.
                    }
                });
    }
}
