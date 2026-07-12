package com.ainote.app.service;

import com.ainote.app.model.memory.MemoryMetricsSnapshotResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryMetricsServiceTest {

    @Test
    void recordsMemoryProductionMetricsAndSnapshotRates() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MemoryMetricsService service = new MemoryMetricsService(registry);

        service.recordCapture("succeeded", "explicit_memory", 2, 1, 10);
        service.recordCapture("denied", "sensitive_content", 0, 0, 40);
        service.recordCapture("skipped", "no_candidates", 0, 0, 50);
        service.recordCapture("failed", "capture_exception", 0, 0, 60);
        service.recordWriteResult(new MemoryWriteService.MemoryWriteResult(2, 1, 1, 3), "explicit_memory");
        service.recordAdvisorSkipped("advisor_disabled");
        service.recordAdvisorResult(true, true, "", 12);
        service.recordAdvisorResult(false, false, "TimeoutException", 20);
        service.recordRetrieval(1, 0, "query_relevant", 5);
        service.recordRetrieval(0, 0, "query_relevant", 15);
        service.recordContextInjection("semantic", 2, 100);
        service.recordContextInjection("episodic", 1, 60);
        service.recordFeedback("wrong_memory");
        service.recordFeedback("outdated");
        service.recordUserDeletion("soft_delete", 2);
        service.recordRetentionPurge(1);

        MemoryMetricsSnapshotResponse snapshot = service.snapshot();

        assertThat(snapshot.captureDecisionCount()).isEqualTo(4);
        assertThat(snapshot.captureAllowedCount()).isEqualTo(1);
        assertThat(snapshot.captureRejectionRate()).isEqualTo(75.0);
        assertThat(snapshot.captureCandidateCount()).isEqualTo(2);
        assertThat(snapshot.captureWrittenMemoryCount()).isEqualTo(1);
        assertThat(snapshot.memoryWriteCount()).isEqualTo(4);
        assertThat(snapshot.memoryWriteSkippedCount()).isEqualTo(3);
        assertThat(snapshot.memoryWriteRate()).isEqualTo(100.0);
        assertThat(snapshot.advisorRequestCount()).isEqualTo(2);
        assertThat(snapshot.advisorFailureCount()).isEqualTo(1);
        assertThat(snapshot.advisorUnavailableCount()).isEqualTo(1);
        assertThat(snapshot.advisorSkippedCount()).isEqualTo(1);
        assertThat(snapshot.advisorFailureRate()).isEqualTo(50.0);
        assertThat(snapshot.retrievalRequestCount()).isEqualTo(2);
        assertThat(snapshot.retrievalHitCount()).isEqualTo(1);
        assertThat(snapshot.retrievalHitRate()).isEqualTo(50.0);
        assertThat(snapshot.contextInjectionCount()).isEqualTo(2);
        assertThat(snapshot.injectedMemoryCount()).isEqualTo(3);
        assertThat(snapshot.averageInjectedMemories()).isEqualTo(1.5);
        assertThat(snapshot.feedbackCount()).isEqualTo(2);
        assertThat(snapshot.wrongWriteFeedbackCount()).isEqualTo(1);
        assertThat(snapshot.wrongWriteFeedbackRate()).isEqualTo(50.0);
        assertThat(snapshot.userDeletionRequestCount()).isEqualTo(1);
        assertThat(snapshot.userDeletedMemoryCount()).isEqualTo(2);
        assertThat(snapshot.retentionPurgedMemoryCount()).isEqualTo(1);
        assertThat(snapshot.captureP95LatencyMs()).isEqualTo(60.0);
        assertThat(snapshot.retrievalP95LatencyMs()).isEqualTo(15.0);

        assertThat(registry.find("ainote.memory.capture.decisions")
                .tag("status", "succeeded")
                .tag("reason", "explicit_memory")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(registry.find("ainote.memory.retrieval.requests")
                .tag("result", "hit")
                .tag("source", "query_relevant")
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void noopSnapshotStaysZeroWithoutMeterRegistry() {
        MemoryMetricsSnapshotResponse snapshot = MemoryMetricsService.noop().snapshot();

        assertThat(snapshot.captureDecisionCount()).isZero();
        assertThat(snapshot.memoryWriteCount()).isZero();
        assertThat(snapshot.advisorFailureRate()).isZero();
        assertThat(snapshot.retrievalHitRate()).isZero();
        assertThat(snapshot.status()).isEqualTo("normal");
    }
}
