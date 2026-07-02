package com.ainote.app.service.chat;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private ChatMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new ChatMetrics(meterRegistry);
    }

    @Test
    void recordsRequestSuccessFallbackAndFailureMetrics() {
        metrics.recordRequest();
        metrics.recordSuccess("agent", 42);
        metrics.recordFallback("circuit_open", 17);
        metrics.recordFallbackLevel("readonly");
        metrics.recordCircuitOpen();
        metrics.recordTotalFailure();

        assertThat(meterRegistry.counter("chat.requests.total").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("chat.requests", "strategy", "agent", "result", "success").count()).isEqualTo(1.0);
        assertThat(meterRegistry.timer("chat.latency", "strategy", "agent").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("chat.fallback", "reason", "circuit_open").count()).isEqualTo(1.0);
        assertThat(meterRegistry.timer("chat.agent.failed.latency").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("chat.fallback.level", "level", "readonly").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("chat.circuit.open").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("chat.total.failure").count()).isEqualTo(1.0);
    }

    @Test
    void recordsStreamLifecycleEventAndErrorMetrics() {
        metrics.recordStreamOpened("eventsource");
        metrics.recordStreamEvent("token");
        metrics.recordStreamClosed("complete");
        metrics.recordStreamSendError("io");
        metrics.recordStreamRejected("post");

        assertThat(meterRegistry.counter("chat.streams.opened", "transport", "eventsource").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("chat.stream.events", "event", "token").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("chat.streams.closed", "reason", "complete").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("chat.stream.send.errors", "reason", "io").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("chat.streams.rejected", "transport", "post").count()).isEqualTo(1.0);
    }

    @Test
    void recordsAgentCapacityMetrics() {
        metrics.recordAgentCapacityRequest("stream", "acquired", 12);
        metrics.recordAgentCapacityRequest("stream", "rejected", 3);

        assertThat(meterRegistry.counter(
                "chat.agent.capacity.requests", "mode", "stream", "result", "acquired").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.counter(
                "chat.agent.capacity.requests", "mode", "stream", "result", "rejected").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.timer(
                "chat.agent.capacity.wait", "mode", "stream", "result", "acquired").count())
                .isEqualTo(1);
        assertThat(meterRegistry.timer(
                "chat.agent.capacity.wait", "mode", "stream", "result", "rejected").count())
                .isEqualTo(1);
    }
}
