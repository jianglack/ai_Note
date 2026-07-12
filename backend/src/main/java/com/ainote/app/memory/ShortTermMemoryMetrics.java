package com.ainote.app.memory;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class ShortTermMemoryMetrics {

    private final Counter loads;
    private final DistributionSummary loadedMessages;
    private final Timer loadLatency;
    private final Counter loadFailures;
    private final Counter appends;
    private final DistributionSummary appendedMessages;
    private final Counter trimmedMessages;
    private final Counter flushRetries;
    private final Counter flushFailures;
    private final MeterRegistry meterRegistry;

    public ShortTermMemoryMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        loads = Counter.builder("chat.memory.load.total").register(meterRegistry);
        loadedMessages = DistributionSummary.builder("chat.memory.loaded.messages").register(meterRegistry);
        loadLatency = Timer.builder("chat.memory.load.latency").publishPercentileHistogram().register(meterRegistry);
        loadFailures = Counter.builder("chat.memory.load.failure.total").register(meterRegistry);
        appends = Counter.builder("chat.memory.append.total").register(meterRegistry);
        appendedMessages = DistributionSummary.builder("chat.memory.appended.messages").register(meterRegistry);
        trimmedMessages = Counter.builder("chat.memory.trimmed.messages").register(meterRegistry);
        flushRetries = Counter.builder("chat.memory.flush.retry.total").register(meterRegistry);
        flushFailures = Counter.builder("chat.memory.flush.failure.total").register(meterRegistry);
    }

    public void recordLoad(int count, long latencyNanos) {
        loads.increment();
        loadedMessages.record(Math.max(0, count));
        loadLatency.record(Math.max(0, latencyNanos), TimeUnit.NANOSECONDS);
    }

    public void recordAppend(int count) {
        appends.increment();
        appendedMessages.record(Math.max(0, count));
    }

    public void recordLoadFailure() { loadFailures.increment(); }

    public void recordTrim(int count) {
        if (count > 0) trimmedMessages.increment(count);
    }

    public void recordFlushRetry() { flushRetries.increment(); }
    public void recordFlushFailure() { flushFailures.increment(); }

    public void recordCompaction(String result, long latencyMs) {
        meterRegistry.counter("chat.memory.compaction.jobs.total", "result", result).increment();
        meterRegistry.timer("chat.memory.compaction.latency", "result", result)
                .record(Math.max(0, latencyMs), TimeUnit.MILLISECONDS);
    }
}
