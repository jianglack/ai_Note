package com.ainote.app.service;

import com.ainote.app.service.chat.ChatMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCapacityLimiterTest {

    @Test
    void rejectsWhenAllGlobalAgentPermitsAreInUseAndRecordsMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AgentCapacityLimiter limiter = new AgentCapacityLimiter(1, 1, new ChatMetrics(meterRegistry));

        AgentCapacityLimiter.Permit held = limiter.tryAcquire("stream");
        try {
            AgentCapacityLimiter.Permit rejected = limiter.tryAcquire("stream");

            assertThat(held.acquired()).isTrue();
            assertThat(rejected.acquired()).isFalse();
            assertThat(limiter.activeCount()).isEqualTo(1);
            assertThat(limiter.availablePermits()).isZero();
            assertThat(meterRegistry.counter(
                    "chat.agent.capacity.requests", "mode", "stream", "result", "acquired").count())
                    .isEqualTo(1.0);
            assertThat(meterRegistry.counter(
                    "chat.agent.capacity.requests", "mode", "stream", "result", "rejected").count())
                    .isEqualTo(1.0);
        } finally {
            held.close();
        }
    }

    @Test
    void releasesPermitSoLaterAgentRequestCanEnter() {
        AgentCapacityLimiter limiter = new AgentCapacityLimiter(1, 1, null);

        AgentCapacityLimiter.Permit first = limiter.tryAcquire("sync");
        first.close();
        AgentCapacityLimiter.Permit second = limiter.tryAcquire("sync");

        try {
            assertThat(first.acquired()).isTrue();
            assertThat(second.acquired()).isTrue();
            assertThat(limiter.activeCount()).isEqualTo(1);
        } finally {
            second.close();
        }
    }

    @Test
    void zeroMaxConcurrentDisablesGlobalLimitForTestsAndSmallDeployments() {
        AgentCapacityLimiter limiter = new AgentCapacityLimiter(0, 0, null);

        AgentCapacityLimiter.Permit first = limiter.tryAcquire("sync");
        AgentCapacityLimiter.Permit second = limiter.tryAcquire("sync");

        assertThat(first.acquired()).isTrue();
        assertThat(second.acquired()).isTrue();
        assertThat(limiter.activeCount()).isZero();
    }
}
