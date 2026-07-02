package com.ainote.app.service;

import com.ainote.app.service.chat.ChatMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AgentCapacityLimiter {

    public static final String BUSY_MESSAGE = "AGENT_BUSY: AI agent is busy. Please retry shortly.";

    private final Semaphore permits;
    private final long acquireTimeoutMs;
    private final ChatMetrics chatMetrics;
    private final AtomicInteger active = new AtomicInteger();

    public AgentCapacityLimiter(
            @Value("${app.agent.capacity.max-concurrent:8}") int maxConcurrent,
            @Value("${app.agent.capacity.acquire-timeout-ms:100}") long acquireTimeoutMs,
            ChatMetrics chatMetrics) {
        this.permits = maxConcurrent > 0 ? new Semaphore(maxConcurrent) : null;
        this.acquireTimeoutMs = Math.max(0, acquireTimeoutMs);
        this.chatMetrics = chatMetrics;
    }

    public Permit tryAcquire(String mode) {
        if (permits == null) {
            return Permit.unlimited();
        }

        long start = System.currentTimeMillis();
        boolean acquired;
        try {
            acquired = permits.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            record(mode, "interrupted", elapsedSince(start));
            return Permit.rejected();
        }

        long waitedMs = elapsedSince(start);
        if (!acquired) {
            record(mode, "rejected", waitedMs);
            return Permit.rejected();
        }

        active.incrementAndGet();
        record(mode, "acquired", waitedMs);
        return new Permit(this, true, false);
    }

    public int activeCount() {
        return active.get();
    }

    public int availablePermits() {
        return permits == null ? Integer.MAX_VALUE : permits.availablePermits();
    }

    private void release() {
        active.decrementAndGet();
        permits.release();
    }

    private void record(String mode, String result, long waitedMs) {
        if (chatMetrics != null) {
            chatMetrics.recordAgentCapacityRequest(mode, result, waitedMs);
        }
    }

    private long elapsedSince(long start) {
        return Math.max(0, System.currentTimeMillis() - start);
    }

    public static final class Permit implements AutoCloseable {
        private final AgentCapacityLimiter limiter;
        private final boolean acquired;
        private final boolean unlimited;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private Permit(AgentCapacityLimiter limiter, boolean acquired, boolean unlimited) {
            this.limiter = limiter;
            this.acquired = acquired;
            this.unlimited = unlimited;
        }

        private static Permit rejected() {
            return new Permit(null, false, false);
        }

        private static Permit unlimited() {
            return new Permit(null, true, true);
        }

        public boolean acquired() {
            return acquired;
        }

        @Override
        public void close() {
            if (acquired && !unlimited && closed.compareAndSet(false, true)) {
                limiter.release();
            }
        }
    }
}
