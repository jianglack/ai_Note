package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.ainote.app.service.MemoryAdvisorFormalBatchEvaluationService.CaseStatus.COMPLETED;
import static com.ainote.app.service.MemoryAdvisorFormalBatchEvaluationService.CaseStatus.ERROR;
import static com.ainote.app.service.MemoryAdvisorFormalBatchEvaluationService.CaseStatus.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

class MemoryAdvisorFailureMetricsServiceTest {

    @Test
    void summarizesAvailabilityRetryReasonsAndLatency() {
        MemoryAdvisorFailureMetricsService service = new MemoryAdvisorFailureMetricsService();
        List<MemoryAdvisorFormalBatchEvaluationService.ProgressEntry> progress = List.of(
                progress("ok", COMPLETED, true, 10, 1, "ok"),
                progress("timeout", TIMEOUT, false, 200, 3, "TIMEOUT"),
                progress("error", ERROR, false, 50, 2, "IOException"));

        MemoryAdvisorFailureMetricsService.FailureMetricsReport report = service.summarize(progress);

        assertThat(report.totalEvaluatedCases()).isEqualTo(3);
        assertThat(report.availableCases()).isEqualTo(1);
        assertThat(report.unavailableCases()).isEqualTo(2);
        assertThat(report.timeoutCases()).isEqualTo(1);
        assertThat(report.errorCases()).isEqualTo(1);
        assertThat(report.retryAttemptCount()).isEqualTo(6);
        assertThat(report.retriedCaseCount()).isEqualTo(2);
        assertThat(report.p95LatencyMillis()).isEqualTo(200);
        assertThat(report.topUnavailableReasons()).extracting(MemoryAdvisorFailureMetricsService.ReasonCount::reason)
                .contains("TIMEOUT", "IOException");
    }

    private static MemoryAdvisorFormalBatchEvaluationService.ProgressEntry progress(
            String id,
            MemoryAdvisorFormalBatchEvaluationService.CaseStatus status,
            boolean available,
            long latencyMillis,
            int attemptCount,
            String reason) {
        MemorySignalAdvisor.AdvisorResult finalResult = available
                ? MemorySignalAdvisor.AdvisorResult.noCapture("none", 0.1, List.of(), reason)
                : MemorySignalAdvisor.AdvisorResult.unavailable(List.of("advisor_failed"), reason);
        return new MemoryAdvisorFormalBatchEvaluationService.ProgressEntry(
                id,
                status,
                finalResult.available(),
                finalResult.shouldCapture(),
                finalResult.memoryType(),
                finalResult.confidence(),
                finalResult.signals(),
                finalResult.reason(),
                MemoryAdvisorRawResult.fromFinal(finalResult),
                latencyMillis,
                "2026-01-01T00:00:00Z",
                attemptCount,
                List.of());
    }
}
