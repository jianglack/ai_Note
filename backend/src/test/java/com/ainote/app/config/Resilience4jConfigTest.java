package com.ainote.app.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class Resilience4jConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(Resilience4jConfigTest::loadApplicationYaml)
            .withUserConfiguration(Resilience4jConfig.class);

    private static void loadApplicationYaml(ConfigurableApplicationContext context) {
        try {
            new YamlPropertySourceLoader()
                    .load("application", new ClassPathResource("application.yml"))
                    .forEach(context.getEnvironment().getPropertySources()::addLast);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Test
    void loadsProductionPoliciesWithoutSpringBoot3AutoConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(CircuitBreakerRegistry.class);
            assertThat(context).hasSingleBean(RetryRegistry.class);
            assertThat(context.getBeansOfType(MeterBinder.class)).hasSize(2);

            CircuitBreakerRegistry circuitBreakers = context.getBean(CircuitBreakerRegistry.class);
            assertThat(circuitBreakers.getAllCircuitBreakers())
                    .extracting(CircuitBreaker::getName)
                    .containsExactlyInAnyOrder(
                            "agent-model", "agent-chat", "chat-model",
                            "embedding-model", "rerank-model", "memory-extract");

            var agentChat = circuitBreakers.circuitBreaker("agent-chat").getCircuitBreakerConfig();
            assertThat(agentChat.getFailureRateThreshold()).isEqualTo(50.0f);
            assertThat(agentChat.getSlowCallDurationThreshold()).isEqualTo(Duration.ofSeconds(60));
            assertThat(agentChat.getSlowCallRateThreshold()).isEqualTo(80.0f);

            var embedding = circuitBreakers.circuitBreaker("embedding-model").getCircuitBreakerConfig();
            assertThat(embedding.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(60_000L);

            var memoryExtract = circuitBreakers.circuitBreaker("memory-extract").getCircuitBreakerConfig();
            assertThat(memoryExtract.getFailureRateThreshold()).isEqualTo(70.0f);

            RetryRegistry retries = context.getBean(RetryRegistry.class);
            assertThat(retries.getAllRetries())
                    .extracting(retry -> retry.getName())
                    .containsExactlyInAnyOrder("agent-model", "chat-model");

            var retry = retries.retry("chat-model").getRetryConfig();
            assertThat(retry.getMaxAttempts()).isEqualTo(3);
            var retryFailure = Either.<Throwable, Object>left(new IOException());
            assertThat(retry.<Object>getIntervalBiFunction().apply(1, retryFailure)).isEqualTo(2_000L);
            assertThat(retry.<Object>getIntervalBiFunction().apply(2, retryFailure)).isEqualTo(4_000L);
            assertThat(retry.getExceptionPredicate().test(new IOException())).isTrue();
            assertThat(retry.getExceptionPredicate().test(new IllegalStateException())).isFalse();
        });
    }
}
