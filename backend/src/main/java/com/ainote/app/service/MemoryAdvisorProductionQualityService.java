package com.ainote.app.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class MemoryAdvisorProductionQualityService {

    private static final int FAILURE_SAMPLE_LIMIT = 50;

    private final MemoryAdvisorReplayEvaluationService advisorReplayEvaluator;
    private final MemoryReplayEvaluationService baselineEvaluator;

    public MemoryAdvisorProductionQualityService(MemoryAdvisorReplayEvaluationService advisorReplayEvaluator,
                                                 MemoryReplayEvaluationService baselineEvaluator) {
        this.advisorReplayEvaluator = advisorReplayEvaluator;
        this.baselineEvaluator = baselineEvaluator;
    }

    public AdvisorReadinessReport evaluate(List<MemoryReplayEvaluationService.MemoryReplayCase> cases,
                                           MemorySignalAdvisor advisor,
                                           AdvisorEvaluationRun run) {
        List<MemoryReplayEvaluationService.MemoryReplayCase> replayCases = cases == null ? List.of() : cases;
        AdvisorEvaluationRun safeRun = run == null ? AdvisorEvaluationRun.production("", "", "", "", "") : run;
        MemorySignalAdvisor effectiveAdvisor = advisor == null ? MemorySignalAdvisor.disabled() : advisor;

        MemoryAdvisorReplayEvaluationService.AdvisorEvaluationReport advisorReport =
                advisorReplayEvaluator.evaluate(replayCases, effectiveAdvisor);
        MemoryReplayEvaluationService.EvaluationReport baselineReport = baselineEvaluator.evaluate(replayCases);

        Set<String> sensitiveCaseIds = sensitiveCaseIds(replayCases);
        AdvisorQualityMetrics metrics = buildMetrics(advisorReport, baselineReport, sensitiveCaseIds);
        List<QualityGateFailure> gateFailures = evaluateGates(safeRun, metrics);
        AdvisorFailureReport failureReport = buildFailureReport(safeRun, advisorReport.failures(), sensitiveCaseIds);

        return new AdvisorReadinessReport(
                safeRun,
                metrics,
                gateFailures.isEmpty(),
                gateFailures,
                advisorReport,
                baselineReport,
                failureReport);
    }

    private AdvisorQualityMetrics buildMetrics(
            MemoryAdvisorReplayEvaluationService.AdvisorEvaluationReport advisorReport,
            MemoryReplayEvaluationService.EvaluationReport baselineReport,
            Set<String> sensitiveCaseIds) {
        long sensitiveFalseAllows = advisorReport.results().stream()
                .filter(result -> sensitiveCaseIds.contains(result.id()))
                .filter(MemoryAdvisorReplayEvaluationService.AdvisorCaseResult::actualShouldCapture)
                .count();
        double sensitiveFalseAllowRate = sensitiveCaseIds.isEmpty()
                ? 0.0
                : (double) sensitiveFalseAllows / sensitiveCaseIds.size();
        double baselineDecisionAccuracy = baselineReport.decisionAccuracy();
        double deltaVsBaseline = advisorReport.captureDecisionAccuracy() - baselineDecisionAccuracy;

        return new AdvisorQualityMetrics(
                advisorReport.totalCases(),
                advisorReport.availabilityRate(),
                1.0 - advisorReport.availabilityRate(),
                advisorReport.captureDecisionAccuracy(),
                advisorReport.falsePositiveRate(),
                advisorReport.falseNegativeRate(),
                advisorReport.memoryTypeAccuracy(),
                sensitiveFalseAllowRate,
                baselineDecisionAccuracy,
                deltaVsBaseline,
                advisorReport.p95LatencyMillis());
    }

    private List<QualityGateFailure> evaluateGates(AdvisorEvaluationRun run, AdvisorQualityMetrics metrics) {
        AdvisorQualityThresholds thresholds = run.thresholds();
        List<QualityGateFailure> failures = new ArrayList<>();

        requireAtLeast(failures, "total_cases", "totalCases", metrics.totalCases(), thresholds.minTotalCases());
        requireAtLeast(failures, "advisor_availability_rate", "availabilityRate",
                metrics.availabilityRate(), thresholds.minAvailabilityRate());
        requireAtLeast(failures, "capture_decision_accuracy", "captureDecisionAccuracy",
                metrics.captureDecisionAccuracy(), thresholds.minCaptureDecisionAccuracy());
        requireAtMost(failures, "false_positive_rate", "falsePositiveRate",
                metrics.falsePositiveRate(), thresholds.maxFalsePositiveRate());
        requireAtMost(failures, "false_negative_rate", "falseNegativeRate",
                metrics.falseNegativeRate(), thresholds.maxFalseNegativeRate());
        requireAtLeast(failures, "memory_type_accuracy", "memoryTypeAccuracy",
                metrics.memoryTypeAccuracy(), thresholds.minMemoryTypeAccuracy());
        requireAtMost(failures, "sensitive_false_allow_rate", "sensitiveFalseAllowRate",
                metrics.sensitiveFalseAllowRate(), thresholds.maxSensitiveFalseAllowRate());
        requireAtMost(failures, "p95_latency_millis", "p95LatencyMillis",
                metrics.p95LatencyMillis(), thresholds.maxP95LatencyMillis());

        double allowedRegression = -thresholds.maxDecisionAccuracyRegression();
        if (metrics.advisorDecisionAccuracyDeltaVsBaseline() < allowedRegression) {
            failures.add(new QualityGateFailure(
                    "decision_accuracy_delta_vs_baseline",
                    "advisorDecisionAccuracyDeltaVsBaseline",
                    metrics.advisorDecisionAccuracyDeltaVsBaseline(),
                    allowedRegression,
                    "advisor decision accuracy regressed versus rule baseline"));
        }

        if (!hasText(run.datasetVersion()) || !hasText(run.modelName()) || !hasText(run.promptVersion())) {
            failures.add(new QualityGateFailure(
                    "run_metadata",
                    "metadataPresent",
                    0.0,
                    1.0,
                    "dataset, model, and prompt versions are required"));
        }

        return List.copyOf(failures);
    }

    private static void requireAtLeast(List<QualityGateFailure> failures,
                                       String gate,
                                       String metric,
                                       double actual,
                                       double expected) {
        if (actual < expected) {
            failures.add(new QualityGateFailure(gate, metric, actual, expected, "metric is below minimum"));
        }
    }

    private static void requireAtMost(List<QualityGateFailure> failures,
                                      String gate,
                                      String metric,
                                      double actual,
                                      double expected) {
        if (actual > expected) {
            failures.add(new QualityGateFailure(gate, metric, actual, expected, "metric is above maximum"));
        }
    }

    private AdvisorFailureReport buildFailureReport(
            AdvisorEvaluationRun run,
            List<MemoryAdvisorReplayEvaluationService.AdvisorCaseResult> failures,
            Set<String> sensitiveCaseIds) {
        List<AdvisorFailureSample> samples = failures.stream()
                .sorted((left, right) -> Boolean.compare(
                        sensitiveCaseIds.contains(right.id()),
                        sensitiveCaseIds.contains(left.id())))
                .limit(FAILURE_SAMPLE_LIMIT)
                .map(failure -> new AdvisorFailureSample(
                        failure.id(),
                        failure.messages(),
                        failure.expectedCaptureAllowed(),
                        failure.actualShouldCapture(),
                        failure.expectedMemoryType(),
                        failure.actualMemoryType(),
                        sensitiveCaseIds.contains(failure.id())))
                .toList();
        return new AdvisorFailureReport(
                run.runId(),
                run.datasetVersion(),
                run.modelName(),
                run.promptVersion(),
                failures.size(),
                samples);
    }

    private static Set<String> sensitiveCaseIds(List<MemoryReplayEvaluationService.MemoryReplayCase> cases) {
        Set<String> sensitiveIds = new LinkedHashSet<>();
        for (MemoryReplayEvaluationService.MemoryReplayCase replayCase : cases) {
            if ("DENY_SENSITIVE".equals(replayCase.expectedDecisionType())
                    || "sensitive_content".equals(replayCase.expectedPolicyReason())
                    || replayCase.id().contains("_sensitive_")) {
                sensitiveIds.add(replayCase.id());
            }
        }
        return Set.copyOf(sensitiveIds);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record AdvisorEvaluationRun(String runId,
                                       String advisorName,
                                       String modelName,
                                       String promptVersion,
                                       String datasetVersion,
                                       AdvisorQualityThresholds thresholds) {
        public AdvisorEvaluationRun {
            runId = runId == null ? "" : runId;
            advisorName = advisorName == null ? "" : advisorName;
            modelName = modelName == null ? "" : modelName;
            promptVersion = promptVersion == null ? "" : promptVersion;
            datasetVersion = datasetVersion == null ? "" : datasetVersion;
            thresholds = thresholds == null ? AdvisorQualityThresholds.productionDefaults() : thresholds;
        }

        public static AdvisorEvaluationRun production(String runId,
                                                      String advisorName,
                                                      String modelName,
                                                      String promptVersion,
                                                      String datasetVersion) {
            return new AdvisorEvaluationRun(
                    runId,
                    advisorName,
                    modelName,
                    promptVersion,
                    datasetVersion,
                    AdvisorQualityThresholds.productionDefaults());
        }
    }

    public record AdvisorQualityThresholds(int minTotalCases,
                                           double minAvailabilityRate,
                                           double minCaptureDecisionAccuracy,
                                           double maxFalsePositiveRate,
                                           double maxFalseNegativeRate,
                                           double minMemoryTypeAccuracy,
                                           double maxSensitiveFalseAllowRate,
                                           long maxP95LatencyMillis,
                                           double maxDecisionAccuracyRegression) {
        public static AdvisorQualityThresholds productionDefaults() {
            return new AdvisorQualityThresholds(
                    4000,
                    0.995,
                    0.995,
                    0.0,
                    0.02,
                    0.98,
                    0.0,
                    1500,
                    0.005);
        }
    }

    public record AdvisorQualityMetrics(int totalCases,
                                        double availabilityRate,
                                        double unavailableRate,
                                        double captureDecisionAccuracy,
                                        double falsePositiveRate,
                                        double falseNegativeRate,
                                        double memoryTypeAccuracy,
                                        double sensitiveFalseAllowRate,
                                        double baselineDecisionAccuracy,
                                        double advisorDecisionAccuracyDeltaVsBaseline,
                                        long p95LatencyMillis) {
    }

    public record QualityGateFailure(String gate,
                                     String metric,
                                     double actual,
                                     double expected,
                                     String message) {
        public QualityGateFailure {
            gate = gate == null ? "" : gate;
            metric = metric == null ? "" : metric;
            message = message == null ? "" : message;
        }
    }

    public record AdvisorFailureReport(String runId,
                                       String datasetVersion,
                                       String modelName,
                                       String promptVersion,
                                       int totalFailures,
                                       List<AdvisorFailureSample> samples) {
        public AdvisorFailureReport {
            runId = runId == null ? "" : runId;
            datasetVersion = datasetVersion == null ? "" : datasetVersion;
            modelName = modelName == null ? "" : modelName;
            promptVersion = promptVersion == null ? "" : promptVersion;
            samples = samples == null ? List.of() : List.copyOf(samples);
        }
    }

    public record AdvisorFailureSample(String id,
                                       List<String> messages,
                                       boolean expectedCaptureAllowed,
                                       boolean actualShouldCapture,
                                       String expectedMemoryType,
                                       String actualMemoryType,
                                       boolean sensitiveBoundary) {
        public AdvisorFailureSample {
            id = id == null ? "" : id;
            messages = messages == null ? List.of() : List.copyOf(messages);
            expectedMemoryType = expectedMemoryType == null ? "" : expectedMemoryType;
            actualMemoryType = actualMemoryType == null ? "none" : actualMemoryType;
        }
    }

    public record AdvisorReadinessReport(AdvisorEvaluationRun run,
                                         AdvisorQualityMetrics metrics,
                                         boolean qualityGatePassed,
                                         List<QualityGateFailure> gateFailures,
                                         MemoryAdvisorReplayEvaluationService.AdvisorEvaluationReport advisorReport,
                                         MemoryReplayEvaluationService.EvaluationReport baselineReport,
                                         AdvisorFailureReport failureReport) {
        public AdvisorReadinessReport {
            gateFailures = gateFailures == null ? List.of() : List.copyOf(gateFailures);
        }
    }
}
