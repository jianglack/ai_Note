package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryAdvisorCalibrationServiceTest {

    private final MemoryAdvisorCalibrationService service = new MemoryAdvisorCalibrationService();

    @Test
    void calibrationUsesRawConfidenceAndExcludesUnavailableRows() {
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases = List.of(
                replayCase("positive", true, "preference", "DENY_TRANSIENT"),
                replayCase("negative", false, "", "DENY_TRANSIENT"),
                replayCase("unavailable", true, "preference", "ALLOW_EXPLICIT"));
        List<MemoryAdvisorFormalBatchEvaluationService.ProgressEntry> progress = List.of(
                progress("positive", true, true, true, "preference", 0.70),
                progress("negative", true, true, false, "none", 0.10),
                unavailableProgress("unavailable"));

        MemoryAdvisorCalibrationService.CalibrationReport report = service.calibrate(
                cases,
                progress,
                0.82,
                MemoryAdvisorProductionQualityService.AdvisorQualityThresholds.productionDefaults());

        assertThat(report.evaluatedCases()).isEqualTo(3);
        assertThat(report.calibratedCases()).isEqualTo(2);
        assertThat(report.excludedUnavailableCases()).isEqualTo(1);
        assertThat(report.candidates()).extracting(MemoryAdvisorCalibrationService.ThresholdCandidate::threshold)
                .contains(0.70);
        assertThat(report.recommendedThreshold()).isEqualTo(0.50);
    }

    @Test
    void calibrationExcludesParseErrorsFromThresholdDenominator() {
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases = List.of(
                replayCase("positive", true, "style", "ALLOW_EXPLICIT"),
                replayCase("parse_error", false, "", "DENY_TRANSIENT"));
        List<MemoryAdvisorFormalBatchEvaluationService.ProgressEntry> progress = List.of(
                progress("positive", true, true, true, "style", 0.90),
                progress("parse_error", true, false, false, "none", 0.0));

        MemoryAdvisorCalibrationService.CalibrationReport report = service.calibrate(
                cases,
                progress,
                0.82,
                MemoryAdvisorProductionQualityService.AdvisorQualityThresholds.productionDefaults());

        assertThat(report.calibratedCases()).isEqualTo(1);
        assertThat(report.excludedParseErrorCases()).isEqualTo(1);
        assertThat(candidate(report, 0.90).captureDecisionAccuracy()).isEqualTo(1.0);
        assertThat(candidate(report, 0.95).falseNegativeRate()).isEqualTo(1.0);
    }

    private static MemoryAdvisorCalibrationService.ThresholdCandidate candidate(
            MemoryAdvisorCalibrationService.CalibrationReport report,
            double threshold) {
        return report.candidates().stream()
                .filter(value -> value.threshold() == threshold)
                .findFirst()
                .orElseThrow();
    }

    private static MemoryAdvisorFormalBatchEvaluationService.ProgressEntry progress(String id,
                                                                                   boolean available,
                                                                                   boolean parsed,
                                                                                   boolean rawShouldCapture,
                                                                                   String rawMemoryType,
                                                                                   double rawConfidence) {
        List<String> rawSignals = rawShouldCapture
                ? List.of("advisor_" + rawMemoryType + "_signal")
                : List.of();
        MemorySignalAdvisor.AdvisorResult finalResult = rawShouldCapture
                ? MemorySignalAdvisor.AdvisorResult.capture(rawMemoryType, rawConfidence, rawSignals, "raw decision")
                : MemorySignalAdvisor.AdvisorResult.noCapture(rawMemoryType, rawConfidence, rawSignals, "raw decision");
        MemoryAdvisorRawResult rawResult = new MemoryAdvisorRawResult(
                available,
                parsed,
                rawShouldCapture,
                rawMemoryType,
                rawConfidence,
                rawSignals,
                "raw decision",
                parsed ? "" : "JsonParseException",
                finalResult);
        return new MemoryAdvisorFormalBatchEvaluationService.ProgressEntry(
                id,
                MemoryAdvisorFormalBatchEvaluationService.CaseStatus.COMPLETED,
                finalResult.available(),
                finalResult.shouldCapture(),
                finalResult.memoryType(),
                finalResult.confidence(),
                finalResult.signals(),
                finalResult.reason(),
                rawResult,
                10,
                "2026-01-01T00:00:00Z",
                1,
                List.of());
    }

    private static MemoryAdvisorFormalBatchEvaluationService.ProgressEntry unavailableProgress(String id) {
        MemorySignalAdvisor.AdvisorResult finalResult = MemorySignalAdvisor.AdvisorResult.unavailable(
                List.of("advisor_failed"), "UnresolvedModelServerException");
        return new MemoryAdvisorFormalBatchEvaluationService.ProgressEntry(
                id,
                MemoryAdvisorFormalBatchEvaluationService.CaseStatus.ERROR,
                false,
                false,
                "none",
                0.0,
                finalResult.signals(),
                finalResult.reason(),
                MemoryAdvisorRawResult.fromFinal(finalResult),
                10,
                "2026-01-01T00:00:00Z",
                1,
                List.of());
    }

    private static MemoryReplayEvaluationService.MemoryReplayCase replayCase(String id,
                                                                            boolean expectedAllowed,
                                                                            String memoryType,
                                                                            String decisionType) {
        return new MemoryReplayEvaluationService.MemoryReplayCase(
                id,
                "user message",
                "assistant output",
                expectedAllowed,
                decisionType,
                memoryType,
                null,
                expectedAllowed ? "expected_capture" : "expected_reject",
                List.of());
    }
}
