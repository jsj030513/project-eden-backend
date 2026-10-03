package com.projecteden.world;

import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.springframework.scheduling.TaskScheduler;

/** Captures real Spring registrations without starting any scheduler threads. */
final class NpcCadenceTestSupport {
    record Registration(Runnable callback, Duration delay) { }

    static final class CapturingScheduler {
        private final List<Registration> registrations = new ArrayList<>();
        final TaskScheduler scheduler = mock(TaskScheduler.class, invocation -> {
            if (invocation.getMethod().getName().equals("getClock")) return Clock.systemUTC();
            if (invocation.getMethod().getName().equals("scheduleWithFixedDelay")) {
                Object[] args = invocation.getArguments();
                registrations.add(new Registration((Runnable) args[0], (Duration) args[args.length - 1]));
            }
            if (invocation.getMethod().getName().startsWith("schedule")) {
                return mock(ScheduledFuture.class);
            }
            return null;
        });

        List<Registration> registrations() { return List.copyOf(registrations); }

    }

    static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-01-01T12:00:00Z");
        void advance(Duration elapsed) { instant = instant.plus(elapsed); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
        @Override public Instant instant() { return instant; }
    }

    private NpcCadenceTestSupport() { }
}
