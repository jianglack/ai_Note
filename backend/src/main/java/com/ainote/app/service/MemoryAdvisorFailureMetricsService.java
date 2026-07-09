package com.ainote.app.service;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MemoryAdvisorFailureMetricsService {

    public FailureMetricsReport summarize(List<MemoryAdvisorFormalBatchEvaluationService.ProgressEntry> progress) {
        List<MemoryAdvisorFormalBatchEvaluationService.ProgressEntry> entries = progress == null ? List.of() : progress;
        int total = entries.size();
        int available = (int) entries.stream().filter(MemoryAdvisorFormalBatchEvaluationService.ProgressEntry::available)
                .count();
        int unavailable = total - available;
        int timeout = (int) entries.stream()
                .filter(entry -> entry.status() == MemoryAdvisorFormalBatchEvaluationService.CaseStatus.TIMEOUT)
                .count();
        int error = (int) entries.stream()
                .filter(entry -> entry.status() == MemoryAdvisorFormalBatchEvaluationService.CaseStatus.ERROR)
                .count();
        int retryAttemptCount = entries.stream()
                .mapToInt(MemoryAdvisorFormalBatchEvaluationService.ProgressEntry::attemptCount)
                .sum();
        int retriedCaseCount = (int) entries.stream()
                .filter(entry -> entry.attemptCount() > 1)
                .count();
        return new FailureMetricsReport(
                total,
                available,
                unavailable,
                ratio(available, total),
                timeout,
                error,
                retryAttemptCount,
                retriedCaseCount,
                p95LatencyMillis(entries),
                topUnavailableReasons(entries));
    }

    private long p95LatencyMillis(List<MemoryAdvisorFormalBatchEvaluationService.ProgressEntry> entries) {
        List<Long> latencies = entries.stream()
                .map(MemoryAdvisorFormalBatchEvaluationService.ProgressEntry::latencyMillis)
                .sorted()
                .toList();
        if (latencies.isEmpty()) {
            return 0L;
        }
        int index = (int) Math.ceil(latencies.size() * 0.95) - 1;
        return latencies.get(Math.max(0, Math.min(index, latencies.size() - 1)));
    }

    private List<ReasonCount> topUnavailableReasons(
            List<MemoryAdvisorFormalBatchEvaluationService.ProgressEntry> entries) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (MemoryAdvisorFormalBatchEvaluationService.ProgressEntry entry : entries) {
            if (entry.available()) {
                continue;
            }
            String reason = entry.reason().isBlank() ? "unknown" : entry.reason();
            counts.merge(reason, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .map(entry -> new ReasonCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ReasonCount::count).reversed()
                        .thenComparing(ReasonCount::reason))
                .toList();
    }

    private static double ratio(int numerator, int denominator) {
        return denominator <= 0 ? 0.0 : (double) numerator / denominator;
    }

    public record FailureMetricsReport(int totalEvaluatedCases,
                                       int availableCases,
                                       int unavailableCases,
                                       double availabilityRate,
                                       int timeoutCases,
                                       int errorCases,
                                       int retryAttemptCount,
                                       int retriedCaseCount,
                                       long p95LatencyMillis,
                                       List<ReasonCount> topUnavailableReasons) {
        public FailureMetricsReport {
            totalEvaluatedCases = Math.max(0, totalEvaluatedCases);
            availableCases = Math.max(0, availableCases);
            unavailableCases = Math.max(0, unavailableCases);
            timeoutCases = Math.max(0, timeoutCases);
            errorCases = Math.max(0, errorCases);
            retryAttemptCount = Math.max(0, retryAttemptCount);
            retriedCaseCount = Math.max(0, retriedCaseCount);
            p95LatencyMillis = Math.max(0L, p95LatencyMillis);
            topUnavailableReasons = topUnavailableReasons == null ? List.of() : List.copyOf(topUnavailableReasons);
        }
    }

    public record ReasonCount(String reason, int count) {
        public ReasonCount {
            reason = reason == null ? "" : reason;
            count = Math.max(0, count);
        }
    }
}
