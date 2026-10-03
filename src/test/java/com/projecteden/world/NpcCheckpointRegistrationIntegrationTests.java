package com.projecteden.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.projecteden.ProjectEdenBackendApplication;
import com.projecteden.world.npc.NpcCheckpointScheduler;
import com.projecteden.world.npc.NpcRuntimeService;
import com.projecteden.world.npc.NpcRuntimeStateRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.FixedDelayTask;

class NpcCheckpointRegistrationIntegrationTests {
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @Import(NpcCheckpointScheduler.class)
    static class SchedulingContext { }

    @Test
    void nonTestProfileRegistersDefaultFiveSecondFixedDelay() {
        assertRegistration(null, Duration.ofMillis(5000));
    }

    @Test
    void nonTestProfileRegistersConfiguredFixedDelay() {
        assertRegistration("1234", Duration.ofMillis(1234));
    }

    @Test
    void testProfileExcludesSchedulerBeanAndRegistration() {
        var capture = new NpcCadenceTestSupport.CapturingScheduler();
        context("test", capture).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(NpcCheckpointScheduler.class);
            assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).isEmpty();
        });
    }

    private void assertRegistration(String override, Duration expected) {
        // Also fail if production stops enabling scheduling; the sliced context
        // uses the same Spring @EnableScheduling machinery, without unrelated beans.
        assertThat(AnnotatedElementUtils.hasAnnotation(
                ProjectEdenBackendApplication.class, EnableScheduling.class)).isTrue();
        var capture = new NpcCadenceTestSupport.CapturingScheduler();
        var runner = context("npc-cadence", capture);
        if (override != null) runner = runner.withPropertyValues("eden.world.npc.checkpoint-ms=" + override);
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(NpcCheckpointScheduler.class);
            var bean = context.getBean(NpcCheckpointScheduler.class);
            var tasks = context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks();
            assertThat(tasks).hasSize(1);
            assertThat(tasks.iterator().next().getTask()).isInstanceOf(FixedDelayTask.class);
            var task = (FixedDelayTask) tasks.iterator().next().getTask();
            assertThat(task.getIntervalDuration()).isEqualTo(expected);
            assertThat(capture.registrations()).hasSize(1);
            assertThat(capture.registrations().getFirst().delay()).isEqualTo(expected);
            assertThat(capture.registrations().getFirst().callback()).isSameAs(task.getRunnable());
            var states = context.getBean(NpcRuntimeStateRepository.class);
            var runtime = context.getBean(NpcRuntimeService.class);
            verifyNoInteractions(states, runtime); // Registration must not run in the background.
            Clock clock = context.getBean(Clock.class);
            LocalDateTime cutoff = LocalDateTime.ofInstant(clock.instant().minusSeconds(5), ZoneOffset.UTC);
            when(states.findDueWorldIds(cutoff, PageRequest.of(0, 100))).thenReturn(List.of(42L));
            capture.registrations().getFirst().callback().run();
            verify(runtime).checkpointWorld(42L);
        });
    }

    private ApplicationContextRunner context(String profile, NpcCadenceTestSupport.CapturingScheduler capture) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                .withUserConfiguration(SchedulingContext.class)
                .withBean("taskScheduler", org.springframework.scheduling.TaskScheduler.class, () -> capture.scheduler)
                .withBean(Clock.class, NpcCadenceTestSupport.MutableClock::new)
                .withBean(NpcRuntimeStateRepository.class, () -> mock(NpcRuntimeStateRepository.class))
                .withBean(NpcRuntimeService.class, () -> mock(NpcRuntimeService.class));
    }
}
