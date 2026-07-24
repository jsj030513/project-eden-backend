package com.projecteden.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

class BenchmarkOrchestrationContextTests {
	@Test void conditionalBenchmarkComponentIsAbsentWithoutFlagAndPresentWithFlag(){ ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(ConditionalFixture.class); runner.run(context->assertThat(context).doesNotHaveBean(ConditionalFixture.class)); runner.withPropertyValues("eden.benchmark.orchestration.enabled=true").run(context->assertThat(context).hasSingleBean(ConditionalFixture.class)); }
	@org.springframework.stereotype.Component @ConditionalOnProperty(prefix="eden.benchmark.orchestration",name="enabled",havingValue="true") static class ConditionalFixture { }
}
