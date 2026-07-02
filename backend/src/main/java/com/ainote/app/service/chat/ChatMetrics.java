package com.ainote.app.service.chat;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class ChatMetrics {

    private final MeterRegistry meterRegistry;

    public ChatMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRequest() {
        meterRegistry.counter("chat.requests.total").increment();
    }

    public void recordSuccess(String strategy, long latencyMs) {
        meterRegistry.counter("chat.requests", "strategy", strategy, "result", "success").increment();
        meterRegistry.timer("chat.latency", "strategy", strategy).record(latencyMs, TimeUnit.MILLISECONDS);
    }

    public void recordFallback(String reason, long latencyMs) {
        meterRegistry.counter("chat.fallback", "reason", reason).increment();
        meterRegistry.timer("chat.agent.failed.latency").record(latencyMs, TimeUnit.MILLISECONDS);
    }

    public void recordFallbackLevel(String level) {
        meterRegistry.counter("chat.fallback.level", "level", level).increment();
    }

    public void recordCircuitOpen() {
        meterRegistry.counter("chat.circuit.open").increment();
    }

    public void recordTotalFailure() {
        meterRegistry.counter("chat.total.failure").increment();
    }

    public void recordStreamOpened(String transport) {
        meterRegistry.counter("chat.streams.opened", "transport", normalizeTag(transport)).increment();
    }

    public void recordStreamEvent(String event) {
        meterRegistry.counter("chat.stream.events", "event", normalizeTag(event)).increment();
    }

    public void recordStreamClosed(String reason) {
        meterRegistry.counter("chat.streams.closed", "reason", normalizeTag(reason)).increment();
    }

    public void recordStreamSendError(String reason) {
        meterRegistry.counter("chat.stream.send.errors", "reason", normalizeTag(reason)).increment();
    }

    public void recordStreamRejected(String transport) {
        meterRegistry.counter("chat.streams.rejected", "transport", normalizeTag(transport)).increment();
    }

    public void recordAgentCapacityRequest(String mode, String result, long waitedMs) {
        String normalizedMode = normalizeTag(mode);
        String normalizedResult = normalizeTag(result);
        meterRegistry.counter("chat.agent.capacity.requests",
                "mode", normalizedMode,
                "result", normalizedResult).increment();
        meterRegistry.timer("chat.agent.capacity.wait",
                "mode", normalizedMode,
                "result", normalizedResult).record(Math.max(0, waitedMs), TimeUnit.MILLISECONDS);
    }

    private String normalizeTag(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
