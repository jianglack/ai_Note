package com.ainote.app.service;

import com.ainote.app.model.memory.MemoryMetricsSnapshotResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

@Service
public class MemoryMetricsService {

    private static final int MAX_LATENCY_SAMPLES = 2048;

    private final MeterRegistry meterRegistry;
    private final boolean enabled;
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DistributionSummary> summaries = new ConcurrentHashMap<>();

    private final LongAdder captureDecisionCount = new LongAdder();
    private final LongAdder captureAllowedCount = new LongAdder();
    private final LongAdder captureRejectedCount = new LongAdder();
    private final LongAdder captureSkippedCount = new LongAdder();
    private final LongAdder captureFailedCount = new LongAdder();
    private final LongAdder captureCandidateCount = new LongAdder();
    private final LongAdder captureWrittenMemoryCount = new LongAdder();

    private final LongAdder memoryCreatedCount = new LongAdder();
    private final LongAdder memoryReinforcedCount = new LongAdder();
    private final LongAdder memorySupersededCount = new LongAdder();
    private final LongAdder memoryWriteSkippedCount = new LongAdder();

    private final LongAdder advisorRequestCount = new LongAdder();
    private final LongAdder advisorFailureCount = new LongAdder();
    private final LongAdder advisorUnavailableCount = new LongAdder();
    private final LongAdder advisorSkippedCount = new LongAdder();

    private final LongAdder userDeletionRequestCount = new LongAdder();
    private final LongAdder userDeletedMemoryCount = new LongAdder();
    private final LongAdder retentionPurgedMemoryCount = new LongAdder();

    private final LongAdder retrievalRequestCount = new LongAdder();
    private final LongAdder retrievalHitCount = new LongAdder();
    private final LongAdder retrievalMissCount = new LongAdder();

    private final LongAdder contextInjectionCount = new LongAdder();
    private final LongAdder semanticInjectedMemoryCount = new LongAdder();
    private final LongAdder episodicInjectedMemoryCount = new LongAdder();

    private final LongAdder feedbackCount = new LongAdder();
    private final LongAdder wrongWriteFeedbackCount = new LongAdder();

    private final Deque<Long> captureLatencySamples = new ArrayDeque<>();
    private final Deque<Long> retrievalLatencySamples = new ArrayDeque<>();
    private final Object captureLatencyLock = new Object();
    private final Object retrievalLatencyLock = new Object();

    @Autowired
    public MemoryMetricsService(MeterRegistry meterRegistry) {
        this(meterRegistry, true);
    }

    private MemoryMetricsService(MeterRegistry meterRegistry, boolean enabled) {
        this.meterRegistry = meterRegistry;
        this.enabled = enabled && meterRegistry != null;
    }

    public static MemoryMetricsService noop() {
        return new MemoryMetricsService(null, false);
    }

    public void recordCapture(String status,
                              String reason,
                              int candidateCount,
                              int writeCount,
                              long latencyMs) {
        String normalizedStatus = tag(status);
        captureDecisionCount.increment();
        switch (normalizedStatus) {
            case "succeeded", "allowed" -> captureAllowedCount.increment();
            case "denied", "rejected" -> captureRejectedCount.increment();
            case "failed" -> captureFailedCount.increment();
            default -> captureSkippedCount.increment();
        }
        add(captureCandidateCount, candidateCount);
        add(captureWrittenMemoryCount, writeCount);
        addLatency(captureLatencySamples, captureLatencyLock, latencyMs);

        incrementCounter("ainote.memory.capture.decisions", "status", normalizedStatus, "reason", tag(reason));
        recordSummary("ainote.memory.capture.candidates", Math.max(0, candidateCount), "status", normalizedStatus);
        recordSummary("ainote.memory.capture.written_memories", Math.max(0, writeCount), "status", normalizedStatus);
        recordTimer("ainote.memory.capture.latency", Math.max(0, latencyMs), "status", normalizedStatus);
    }

    public void recordWriteResult(MemoryWriteService.MemoryWriteResult result, String reason) {
        if (result == null) {
            return;
        }
        add(memoryCreatedCount, result.created());
        add(memoryReinforcedCount, result.reinforced());
        add(memorySupersededCount, result.superseded());
        add(memoryWriteSkippedCount, result.skipped());

        incrementCounterBy("ainote.memory.writes", result.created(), "outcome", "created", "reason", tag(reason));
        incrementCounterBy("ainote.memory.writes", result.reinforced(), "outcome", "reinforced", "reason", tag(reason));
        incrementCounterBy("ainote.memory.writes", result.superseded(), "outcome", "superseded", "reason", tag(reason));
        incrementCounterBy("ainote.memory.writes", result.skipped(), "outcome", "skipped", "reason", tag(reason));
    }

    public void recordAdvisorSkipped(String reason) {
        advisorSkippedCount.increment();
        incrementCounter("ainote.memory.advisor.skipped", "reason", tag(reason));
    }

    public void recordAdvisorResult(boolean available,
                                    boolean parsed,
                                    String failureReason,
                                    long latencyMs) {
        advisorRequestCount.increment();
        boolean failed = !available || !parsed;
        if (failed) {
            advisorFailureCount.increment();
        }
        if (!available) {
            advisorUnavailableCount.increment();
        }
        String status = failed ? "failed" : "succeeded";
        incrementCounter("ainote.memory.advisor.requests", "status", status, "reason", tag(failureReason));
        recordTimer("ainote.memory.advisor.latency", Math.max(0, latencyMs), "status", status);
    }

    public void recordRetrieval(int semanticCount,
                                int episodicCount,
                                String source,
                                long latencyMs) {
        retrievalRequestCount.increment();
        boolean hit = semanticCount > 0 || episodicCount > 0;
        if (hit) {
            retrievalHitCount.increment();
        } else {
            retrievalMissCount.increment();
        }
        addLatency(retrievalLatencySamples, retrievalLatencyLock, latencyMs);
        String result = hit ? "hit" : "miss";
        incrementCounter("ainote.memory.retrieval.requests", "result", result, "source", tag(source));
        recordSummary("ainote.memory.retrieval.returned_memories", Math.max(0, semanticCount), "kind", "semantic");
        recordSummary("ainote.memory.retrieval.returned_memories", Math.max(0, episodicCount), "kind", "episodic");
        recordTimer("ainote.memory.retrieval.latency", Math.max(0, latencyMs), "result", result);
    }

    public void recordContextInjection(String kind, int memoryCount, int estimatedTokens) {
        int safeCount = Math.max(0, memoryCount);
        if (safeCount == 0) {
            return;
        }
        String normalizedKind = tag(kind);
        contextInjectionCount.increment();
        if ("episodic".equals(normalizedKind)) {
            add(episodicInjectedMemoryCount, safeCount);
        } else {
            add(semanticInjectedMemoryCount, safeCount);
        }
        incrementCounter("ainote.memory.context.injections", "kind", normalizedKind);
        recordSummary("ainote.memory.context.injected_memories", safeCount, "kind", normalizedKind);
        recordSummary("ainote.memory.context.injected_tokens", Math.max(0, estimatedTokens), "kind", normalizedKind);
    }

    public void recordFeedback(String feedbackType) {
        String normalizedType = tag(feedbackType);
        feedbackCount.increment();
        if ("wrong_memory".equals(normalizedType) || "should_not_remember".equals(normalizedType)) {
            wrongWriteFeedbackCount.increment();
            incrementCounter("ainote.memory.feedback.wrong_write", "type", normalizedType);
        }
        incrementCounter("ainote.memory.feedback.total", "type", normalizedType);
    }

    public void recordUserDeletion(String mode, int memoryCount) {
        userDeletionRequestCount.increment();
        add(userDeletedMemoryCount, memoryCount);
        incrementCounter("ainote.memory.user_deletions", "mode", tag(mode));
        recordSummary("ainote.memory.user_deleted_memories", Math.max(0, memoryCount), "mode", tag(mode));
    }

    public void recordRetentionPurge(int memoryCount) {
        add(retentionPurgedMemoryCount, memoryCount);
        incrementCounter("ainote.memory.retention.purges", "mode", "deleted_retention");
        recordSummary("ainote.memory.retention.purged_memories", Math.max(0, memoryCount), "mode", "deleted_retention");
    }

    public MemoryMetricsSnapshotResponse snapshot() {
        long captureTotal = captureDecisionCount.sum();
        long writeTotal = memoryCreatedCount.sum() + memoryReinforcedCount.sum() + memorySupersededCount.sum();
        long rejectedOrSkipped = captureRejectedCount.sum() + captureSkippedCount.sum() + captureFailedCount.sum();
        long advisorRequests = advisorRequestCount.sum();
        long retrievalRequests = retrievalRequestCount.sum();
        long injectedTotal = semanticInjectedMemoryCount.sum() + episodicInjectedMemoryCount.sum();
        long injections = contextInjectionCount.sum();
        long feedbackTotal = feedbackCount.sum();

        double advisorFailureRate = rate(advisorFailureCount.sum(), advisorRequests);
        double captureRejectionRate = rate(rejectedOrSkipped, captureTotal);
        double retrievalHitRate = rate(retrievalHitCount.sum(), retrievalRequests);
        double wrongWriteFeedbackRate = rate(wrongWriteFeedbackCount.sum(), feedbackTotal);
        double captureP95 = percentile(captureLatencySamples, captureLatencyLock, 0.95);
        double retrievalP95 = percentile(retrievalLatencySamples, retrievalLatencyLock, 0.95);

        String status = advisorFailureRate <= 5.0
                && captureRejectionRate <= 80.0
                && captureP95 <= 2_000.0
                && retrievalP95 <= 1_000.0
                ? "normal"
                : "attention";

        return new MemoryMetricsSnapshotResponse(
                status,
                LocalDateTime.now(),
                captureTotal,
                captureAllowedCount.sum(),
                captureRejectedCount.sum(),
                captureSkippedCount.sum(),
                captureFailedCount.sum(),
                round1(captureRejectionRate),
                captureCandidateCount.sum(),
                captureWrittenMemoryCount.sum(),
                writeTotal,
                memoryCreatedCount.sum(),
                memoryReinforcedCount.sum(),
                memorySupersededCount.sum(),
                memoryWriteSkippedCount.sum(),
                round1(rate(writeTotal, captureTotal)),
                advisorRequests,
                advisorFailureCount.sum(),
                advisorUnavailableCount.sum(),
                advisorSkippedCount.sum(),
                round1(advisorFailureRate),
                userDeletionRequestCount.sum(),
                userDeletedMemoryCount.sum(),
                round1(rate(userDeletionRequestCount.sum(), captureTotal)),
                retrievalRequests,
                retrievalHitCount.sum(),
                retrievalMissCount.sum(),
                round1(retrievalHitRate),
                injections,
                semanticInjectedMemoryCount.sum(),
                episodicInjectedMemoryCount.sum(),
                injectedTotal,
                round1(injections == 0 ? 0.0 : injectedTotal * 1.0 / injections),
                feedbackTotal,
                wrongWriteFeedbackCount.sum(),
                round1(wrongWriteFeedbackRate),
                retentionPurgedMemoryCount.sum(),
                round1(captureP95),
                round1(retrievalP95)
        );
    }

    private void incrementCounter(String name, String... tags) {
        if (enabled) {
            counter(name, tags).increment();
        }
    }

    private void incrementCounterBy(String name, int count, String... tags) {
        if (enabled && count > 0) {
            counter(name, tags).increment(count);
        }
    }

    private void recordTimer(String name, long latencyMs, String... tags) {
        if (enabled) {
            timer(name, tags).record(latencyMs, TimeUnit.MILLISECONDS);
        }
    }

    private void recordSummary(String name, double amount, String... tags) {
        if (enabled) {
            summary(name, tags).record(amount);
        }
    }

    private Counter counter(String name, String... tags) {
        return counters.computeIfAbsent(name + "|" + String.join("|", tags),
                ignored -> Counter.builder(name).tags(tags).register(meterRegistry));
    }

    private Timer timer(String name, String... tags) {
        return timers.computeIfAbsent(name + "|" + String.join("|", tags),
                ignored -> Timer.builder(name).tags(tags).publishPercentileHistogram().register(meterRegistry));
    }

    private DistributionSummary summary(String name, String... tags) {
        return summaries.computeIfAbsent(name + "|" + String.join("|", tags),
                ignored -> DistributionSummary.builder(name).tags(tags).register(meterRegistry));
    }

    private void add(LongAdder adder, int value) {
        if (value > 0) {
            adder.add(value);
        }
    }

    private void addLatency(Deque<Long> samples, Object lock, long latencyMs) {
        long safeLatency = Math.max(0, latencyMs);
        synchronized (lock) {
            if (samples.size() >= MAX_LATENCY_SAMPLES) {
                samples.removeFirst();
            }
            samples.addLast(safeLatency);
        }
    }

    private double percentile(Deque<Long> samples, Object lock, double percentile) {
        List<Long> copy;
        synchronized (lock) {
            if (samples.isEmpty()) {
                return 0.0;
            }
            copy = new ArrayList<>(samples);
        }
        copy.sort(Comparator.naturalOrder());
        int index = (int) Math.ceil(percentile * copy.size()) - 1;
        index = Math.max(0, Math.min(copy.size() - 1, index));
        return copy.get(index);
    }

    private double rate(long numerator, long denominator) {
        return denominator <= 0 ? 0.0 : numerator * 100.0 / denominator;
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String tag(String value) {
        String normalized = value == null || value.isBlank()
                ? "unknown"
                : value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9_\\-]+", "_");
        normalized = normalized.replaceAll("_+", "_");
        if (normalized.length() > 48) {
            normalized = normalized.substring(0, 48);
        }
        return normalized.isBlank() ? "unknown" : normalized;
    }

}
