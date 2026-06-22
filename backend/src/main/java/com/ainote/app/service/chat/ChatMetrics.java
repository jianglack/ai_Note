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
}
