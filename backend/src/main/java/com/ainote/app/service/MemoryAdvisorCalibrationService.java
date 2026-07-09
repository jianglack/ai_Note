package com.ainote.app.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MemoryAdvisorCalibrationService {

    private static final List<Double> CANDIDATE_THRESHOLDS = List.of(
            0.50, 0.55, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.90, 0.95);

    public CalibrationReport calibrate(
            List<MemoryReplayEvaluationService.MemoryReplayCase> cases,
            List<MemoryAdvisorFormalBatchEvaluationService.ProgressEntry> progress,
            double currentMinConfidence,
            MemoryAdvisorProductionQualityService.AdvisorQualityThresholds thresholds) {
        List<MemoryReplayEvaluationService.MemoryReplayCase> safeCases = cases == null ? List.of() : cases;
        List<MemoryAdvisorFormalBatchEvaluationService.ProgressEntry> safeProgress =
                progress == null ? List.of() : progress;
        MemoryAdvisorProductionQualityService.AdvisorQualityThresholds safeThresholds = thresholds == null
                ? MemoryAdvisorProductionQualityService.AdvisorQualityThresholds.productionDefaults()
                : thresholds;
        Map<String, MemoryReplayEvaluationService.MemoryReplayCase> caseById = safeCases.stream()
                .collect(LinkedHashMap::new, (map, replayCase) -> map.put(replayCase.id(), replayCase), Map::putAll);

        List<CalibrationRow> rows = safeProgress.stream()
                .map(entry -> row(caseById.get(entry.caseId()), entry))
                .toList();
        List<CalibrationRow> calibratedRows = rows.stream()
                .filter(row -> row.exclusion() == Exclusion.NONE)
                .toList();
        int positiveCases = (int) calibratedRows.stream().filter(CalibrationRow::expectedCaptureAllowed).count();
        int negativeCases = calibratedRows.size() - positiveCases;
        List<ThresholdCandidate> candidates = CANDIDATE_THRESHOLDS.stream()
                .map(threshold -> candidate(threshold, calibratedRows, positiveCases, negativeCases, safeThresholds))
                .toList();
        ThresholdCandidate recommended = candidates.stream()
                .filter(ThresholdCandidate::productionThresholdsPassed)
                .findFirst()
                .orElse(null);
        boolean deployableThresholdFound = recommended != null;

        return new CalibrationReport(
                safeCases.size(),
                calibratedRows.size(),
                positiveCases,
                negativeCases,
                count(rows, Exclusion.UNAVAILABLE),
                count(rows, Exclusion.PARSE_ERROR),
                count(rows, Exclusion.OTHER_ERROR),
                currentMinConfidence,
                deployableThresholdFound ? recommended.threshold() : currentMinConfidence,
                deployableThresholdFound,
                deployableThresholdFound
                        ? "lowest candidate threshold passed production quality metrics"
                        : "no candidate threshold passed production quality metrics",
                candidates);
    }

    private ThresholdCandidate candidate(
            double threshold,
            List<CalibrationRow> rows,
            int positiveCases,
            int negativeCases,
            MemoryAdvisorProductionQualityService.AdvisorQualityThresholds thresholds) {
        int correctDecision = 0;
        int falsePositive = 0;
        int falseNegative = 0;
        int typedPositiveCases = 0;
        int correctMemoryType = 0;
        int sensitiveCases = 0;
        int sensitiveFalseAllows = 0;
        for (CalibrationRow row : rows) {
            boolean predictedCapture = row.rawResult().rawShouldCapture()
                    && row.rawResult().rawConfidence() >= threshold
                    && !"none".equals(row.rawResult().rawMemoryType());
            if (predictedCapture == row.expectedCaptureAllowed()) {
                correctDecision++;
            }
            if (predictedCapture && !row.expectedCaptureAllowed()) {
                falsePositive++;
            }
            if (!predictedCapture && row.expectedCaptureAllowed()) {
                falseNegative++;
            }
            if (row.expectedCaptureAllowed() && hasText(row.expectedMemoryType())) {
                typedPositiveCases++;
                String predictedMemoryType = predictedCapture ? row.rawResult().rawMemoryType() : "none";
                if (row.expectedMemoryType().equals(predictedMemoryType)) {
                    correctMemoryType++;
                }
            }
            if (row.sensitive()) {
                sensitiveCases++;
                if (predictedCapture) {
                    sensitiveFalseAllows++;
                }
            }
        }
        double captureDecisionAccuracy = ratio(correctDecision, rows.size());
        double falsePositiveRate = ratio(falsePositive, negativeCases);
        double falseNegativeRate = ratio(falseNegative, positiveCases);
        double memoryTypeAccuracy = ratio(correctMemoryType, typedPositiveCases);
        double sensitiveFalseAllowRate = ratio(sensitiveFalseAllows, sensitiveCases);
        boolean passed = captureDecisionAccuracy >= thresholds.minCaptureDecisionAccuracy()
                && falsePositiveRate <= thresholds.maxFalsePositiveRate()
                && falseNegativeRate <= thresholds.maxFalseNegativeRate()
                && memoryTypeAccuracy >= thresholds.minMemoryTypeAccuracy()
                && sensitiveFalseAllowRate <= thresholds.maxSensitiveFalseAllowRate();
        return new ThresholdCandidate(
                threshold,
                captureDecisionAccuracy,
                falsePositiveRate,
                falseNegativeRate,
                memoryTypeAccuracy,
                sensitiveFalseAllowRate,
                passed);
    }

    private CalibrationRow row(MemoryReplayEvaluationService.MemoryReplayCase replayCase,
                               MemoryAdvisorFormalBatchEvaluationService.ProgressEntry entry) {
        if (replayCase == null || entry == null || !entry.available() || !entry.rawResult().available()) {
            return CalibrationRow.excluded(Exclusion.UNAVAILABLE);
        }
        if (!entry.rawResult().parsed()) {
            return CalibrationRow.excluded(Exclusion.PARSE_ERROR);
        }
        if (entry.status() != MemoryAdvisorFormalBatchEvaluationService.CaseStatus.COMPLETED) {
            return CalibrationRow.excluded(Exclusion.OTHER_ERROR);
        }
        return new CalibrationRow(
                replayCase.expectedCaptureAllowed(),
                replayCase.expectedMemoryType(),
                isSensitive(replayCase),
                entry.rawResult(),
                Exclusion.NONE);
    }

    private boolean isSensitive(MemoryReplayEvaluationService.MemoryReplayCase replayCase) {
        return containsIgnoreCase(replayCase.expectedDecisionType(), "SENSITIVE")
                || containsIgnoreCase(replayCase.expectedPolicyReason(), "sensitive")
                || replayCase.expectedSignals().stream().anyMatch(signal -> containsIgnoreCase(signal, "sensitive"));
    }

    private int count(List<CalibrationRow> rows, Exclusion exclusion) {
        return (int) rows.stream().filter(row -> row.exclusion() == exclusion).count();
    }

    private static double ratio(int numerator, int denominator) {
        return denominator <= 0 ? 0.0 : (double) numerator / denominator;
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        return value != null
                && needle != null
                && value.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private enum Exclusion {
        NONE,
        UNAVAILABLE,
        PARSE_ERROR,
        OTHER_ERROR
    }

    private record CalibrationRow(boolean expectedCaptureAllowed,
                                  String expectedMemoryType,
                                  boolean sensitive,
                                  MemoryAdvisorRawResult rawResult,
                                  Exclusion exclusion) {
        static CalibrationRow excluded(Exclusion exclusion) {
            return new CalibrationRow(false, "", false, MemoryAdvisorRawResult.fromFinal(
                    MemorySignalAdvisor.AdvisorResult.unavailable(List.of("advisor_excluded"), exclusion.name())),
                    exclusion);
        }
    }

    public record CalibrationReport(int evaluatedCases,
                                    int calibratedCases,
                                    int positiveCases,
                                    int negativeCases,
                                    int excludedUnavailableCases,
                                    int excludedParseErrorCases,
                                    int excludedOtherErrorCases,
                                    double currentMinConfidence,
                                    double recommendedThreshold,
                                    boolean deployableThresholdFound,
                                    String rationale,
                                    List<ThresholdCandidate> candidates) {
        public CalibrationReport {
            evaluatedCases = Math.max(0, evaluatedCases);
            calibratedCases = Math.max(0, calibratedCases);
            positiveCases = Math.max(0, positiveCases);
            negativeCases = Math.max(0, negativeCases);
            excludedUnavailableCases = Math.max(0, excludedUnavailableCases);
            excludedParseErrorCases = Math.max(0, excludedParseErrorCases);
            excludedOtherErrorCases = Math.max(0, excludedOtherErrorCases);
            rationale = rationale == null ? "" : rationale;
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public record ThresholdCandidate(double threshold,
                                     double captureDecisionAccuracy,
                                     double falsePositiveRate,
                                     double falseNegativeRate,
                                     double memoryTypeAccuracy,
                                     double sensitiveFalseAllowRate,
                                     boolean productionThresholdsPassed) {
    }
}
