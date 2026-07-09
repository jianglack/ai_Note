package com.ainote.app.service;

import java.util.List;

public record MemoryAdvisorRawResult(boolean available,
                                     boolean parsed,
                                     boolean rawShouldCapture,
                                     String rawMemoryType,
                                     double rawConfidence,
                                     List<String> rawSignals,
                                     String rawReason,
                                     String failureReason,
                                     MemorySignalAdvisor.AdvisorResult finalResult) {
    public MemoryAdvisorRawResult {
        rawMemoryType = rawMemoryType == null ? "none" : rawMemoryType;
        rawSignals = rawSignals == null ? List.of() : List.copyOf(rawSignals);
        rawReason = rawReason == null ? "" : rawReason;
        failureReason = failureReason == null ? "" : failureReason;
        finalResult = finalResult == null
                ? MemorySignalAdvisor.AdvisorResult.unavailable(
                List.of("advisor_missing_final_result"), "missing final result")
                : finalResult;
    }

    public static MemoryAdvisorRawResult fromFinal(MemorySignalAdvisor.AdvisorResult result) {
        MemorySignalAdvisor.AdvisorResult safe = result == null
                ? MemorySignalAdvisor.AdvisorResult.unavailable(List.of("advisor_null_result"), "null advisor result")
                : result;
        return new MemoryAdvisorRawResult(
                safe.available(),
                safe.available(),
                safe.shouldCapture(),
                safe.memoryType(),
                safe.confidence(),
                safe.signals(),
                safe.reason(),
                safe.available() ? "" : safe.reason(),
                safe);
    }
}
