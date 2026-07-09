package com.ainote.app.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class MemoryAdvisorReleaseGateService {

    static final long DEFAULT_MAX_REPORT_AGE_HOURS = 168L;

    private final MemoryAdvisorPromptRegistry promptRegistry;
    private final long maxReportAgeHours;
    private final Clock clock;

    public MemoryAdvisorReleaseGateService(MemoryAdvisorPromptRegistry promptRegistry) {
        this(promptRegistry, defaultMaxReportAgeHours(), Clock.systemUTC());
    }

    MemoryAdvisorReleaseGateService(MemoryAdvisorPromptRegistry promptRegistry, long maxReportAgeHours) {
        this(promptRegistry, maxReportAgeHours, Clock.systemUTC());
    }

    MemoryAdvisorReleaseGateService(MemoryAdvisorPromptRegistry promptRegistry,
                                    long maxReportAgeHours,
                                    Clock clock) {
        this.promptRegistry = promptRegistry == null ? new MemoryAdvisorPromptRegistry() : promptRegistry;
        this.maxReportAgeHours = maxReportAgeHours <= 0 ? DEFAULT_MAX_REPORT_AGE_HOURS : maxReportAgeHours;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public ReleaseGateDecision evaluateReadinessOnly(
            MemoryAdvisorProductionQualityService.AdvisorReadinessReport readinessReport) {
        MemoryAdvisorProductionQualityService.AdvisorEvaluationRun run = readinessReport == null
                ? null
                : readinessReport.run();
        List<String> blockReasons = new ArrayList<>();
        blockReasons.add("missing_release_readiness_package");
        if (readinessReport == null) {
            blockReasons.add("missing_readiness_report");
        } else if (!readinessReport.qualityGatePassed()) {
            blockReasons.add("readiness_quality_gate_failed");
        }
        return decision(
                ReleaseGateStatus.BLOCKED,
                run == null ? "" : run.runId(),
                run == null ? "" : run.datasetVersion(),
                run == null ? "" : run.modelName(),
                run == null ? "" : run.promptVersion(),
                "",
                null,
                "",
                readinessReport == null || readinessReport.metrics() == null ? 0 : readinessReport.metrics().totalCases(),
                readinessReport != null && readinessReport.qualityGatePassed(),
                blockReasons,
                "",
                "");
    }

    public ReleaseGateDecision evaluate(MemoryAdvisorReleaseReadinessPackage releasePackage) {
        if (releasePackage == null) {
            return decision(
                    ReleaseGateStatus.BLOCKED,
                    "",
                    "",
                    "",
                    "",
                    "",
                    null,
                    "",
                    0,
                    false,
                    List.of("missing_release_readiness_package"),
                    "",
                    "");
        }
        List<String> blockReasons = new ArrayList<>();
        MemoryAdvisorPromptRegistry.PromptMetadata promptMetadata = releasePackage.promptMetadata();
        Optional<MemoryAdvisorPromptRegistry.PromptMetadata> registered =
                promptRegistry.find(releasePackage.promptVersion());
        if (registered.isEmpty()) {
            blockReasons.add("prompt_version_unknown");
        }
        if (promptMetadata == null) {
            blockReasons.add("prompt_metadata_missing");
        } else {
            if (!releasePackage.promptVersion().equals(promptMetadata.promptVersion())) {
                blockReasons.add("prompt_version_mismatch");
            }
            registered.ifPresent(registeredMetadata -> {
                if (!registeredMetadata.promptHash().equals(promptMetadata.promptHash())) {
                    blockReasons.add("prompt_hash_mismatch");
                }
            });
            if (promptMetadata.status() == MemoryAdvisorPromptRegistry.PromptStatus.DEPRECATED) {
                blockReasons.add("prompt_deprecated");
            }
        }

        MemoryAdvisorProductionQualityService.AdvisorQualityThresholds thresholds = thresholds(releasePackage);
        if (releasePackage.readinessReport() == null) {
            blockReasons.add("missing_readiness_report");
        } else if (!releasePackage.readinessReport().qualityGatePassed()) {
            blockReasons.add("readiness_quality_gate_failed");
        }
        if (releasePackage.calibrationReport() == null) {
            blockReasons.add("missing_calibration_report");
        } else {
            if (!releasePackage.calibrationReport().deployableThresholdFound()) {
                blockReasons.add("no_deployable_threshold");
            }
            if (recommendedCandidate(releasePackage.calibrationReport())
                    .map(candidate -> candidate.sensitiveFalseAllowRate() > thresholds.maxSensitiveFalseAllowRate())
                    .orElse(false)) {
                blockReasons.add("sensitive_false_allow");
            }
        }
        if (releasePackage.abComparisonReport() == null) {
            blockReasons.add("missing_ab_comparison_report");
        } else if (!releasePackage.abComparisonReport().advisorRegressions().isEmpty()
                || releasePackage.abComparisonReport().advisorAccuracyDeltaVsRule()
                < -thresholds.maxDecisionAccuracyRegression()) {
            blockReasons.add("advisor_regression_vs_rule_policy");
        }
        if (releasePackage.failureMetricsReport() == null) {
            blockReasons.add("missing_failure_metrics_report");
        } else {
            if (releasePackage.failureMetricsReport().availabilityRate() < thresholds.minAvailabilityRate()) {
                blockReasons.add("availability_below_threshold");
            }
            if (releasePackage.failureMetricsReport().timeoutCases() > 0) {
                blockReasons.add("timeout_cases_present");
            }
            if (releasePackage.failureMetricsReport().errorCases() > 0) {
                blockReasons.add("error_cases_present");
            }
            if (releasePackage.failureMetricsReport().p95LatencyMillis() > thresholds.maxP95LatencyMillis()) {
                blockReasons.add("p95_latency_exceeded");
            }
        }
        validateReportFreshness(releasePackage.completedAt(), blockReasons);

        MemoryAdvisorPromptRegistry.PromptStatus promptStatus = promptMetadata == null ? null : promptMetadata.status();
        String approvalAction = promptStatus == MemoryAdvisorPromptRegistry.PromptStatus.CANDIDATE
                ? "promote_prompt_to_approved"
                : "";
        ReleaseGateStatus status = blockReasons.isEmpty() ? ReleaseGateStatus.PASS : ReleaseGateStatus.BLOCKED;
        return decision(
                status,
                releasePackage.runId(),
                releasePackage.datasetVersion(),
                releasePackage.modelName(),
                releasePackage.promptVersion(),
                promptMetadata == null ? "" : promptMetadata.promptHash(),
                promptStatus,
                approvalAction,
                evaluatedCases(releasePackage),
                releasePackage.readinessReport() != null && releasePackage.readinessReport().qualityGatePassed(),
                blockReasons,
                releasePackage.progressPath(),
                releasePackage.batchReportPath());
    }

    private Optional<MemoryAdvisorCalibrationService.ThresholdCandidate> recommendedCandidate(
            MemoryAdvisorCalibrationService.CalibrationReport report) {
        return report.candidates().stream()
                .filter(candidate -> candidate.threshold() == report.recommendedThreshold())
                .findFirst();
    }

    private MemoryAdvisorProductionQualityService.AdvisorQualityThresholds thresholds(
            MemoryAdvisorReleaseReadinessPackage releasePackage) {
        if (releasePackage.readinessReport() == null
                || releasePackage.readinessReport().run() == null
                || releasePackage.readinessReport().run().thresholds() == null) {
            return MemoryAdvisorProductionQualityService.AdvisorQualityThresholds.productionDefaults();
        }
        return releasePackage.readinessReport().run().thresholds();
    }

    private int evaluatedCases(MemoryAdvisorReleaseReadinessPackage releasePackage) {
        if (releasePackage.failureMetricsReport() != null) {
            return releasePackage.failureMetricsReport().totalEvaluatedCases();
        }
        if (releasePackage.calibrationReport() != null) {
            return releasePackage.calibrationReport().evaluatedCases();
        }
        if (releasePackage.readinessReport() != null && releasePackage.readinessReport().metrics() != null) {
            return releasePackage.readinessReport().metrics().totalCases();
        }
        return 0;
    }

    private void validateReportFreshness(String completedAt, List<String> blockReasons) {
        if (completedAt == null || completedAt.isBlank()) {
            blockReasons.add("report_timestamp_invalid");
            return;
        }
        try {
            Instant completed = Instant.parse(completedAt);
            Instant now = Instant.now(clock);
            if (completed.isAfter(now.plus(Duration.ofMinutes(1)))) {
                blockReasons.add("report_timestamp_invalid");
                return;
            }
            if (Duration.between(completed, now).toHours() > maxReportAgeHours) {
                blockReasons.add("report_stale");
            }
        } catch (DateTimeParseException e) {
            blockReasons.add("report_timestamp_invalid");
        }
    }

    private ReleaseGateDecision decision(ReleaseGateStatus status,
                                         String runId,
                                         String datasetVersion,
                                         String modelName,
                                         String promptVersion,
                                         String promptHash,
                                         MemoryAdvisorPromptRegistry.PromptStatus promptStatus,
                                         String approvalActionRequired,
                                         int evaluatedCases,
                                         boolean qualityGatePassed,
                                         List<String> blockReasons,
                                         String progressPath,
                                         String batchReportPath) {
        return new ReleaseGateDecision(
                status,
                runId,
                datasetVersion,
                modelName,
                promptVersion,
                promptHash,
                promptStatus,
                approvalActionRequired,
                evaluatedCases,
                qualityGatePassed,
                blockReasons,
                Instant.now(clock).toString(),
                progressPath,
                batchReportPath);
    }

    private static long defaultMaxReportAgeHours() {
        String value = System.getenv("MEMORY_ADVISOR_RELEASE_GATE_MAX_REPORT_AGE_HOURS");
        if (value == null || value.isBlank()) {
            return DEFAULT_MAX_REPORT_AGE_HOURS;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_REPORT_AGE_HOURS;
        }
    }

    public enum ReleaseGateStatus {
        PASS,
        BLOCKED
    }

    public record MemoryAdvisorReleaseReadinessPackage(
            String runId,
            String datasetVersion,
            String modelName,
            String promptVersion,
            String completedAt,
            String progressPath,
            String batchReportPath,
            MemoryAdvisorPromptRegistry.PromptMetadata promptMetadata,
            MemoryAdvisorProductionQualityService.AdvisorReadinessReport readinessReport,
            MemoryAdvisorCalibrationService.CalibrationReport calibrationReport,
            MemoryAdvisorAbComparisonService.AbComparisonReport abComparisonReport,
            MemoryAdvisorFailureMetricsService.FailureMetricsReport failureMetricsReport) {
        public MemoryAdvisorReleaseReadinessPackage {
            runId = runId == null ? "" : runId;
            datasetVersion = datasetVersion == null ? "" : datasetVersion;
            modelName = modelName == null ? "" : modelName;
            promptVersion = promptVersion == null ? "" : promptVersion;
            completedAt = completedAt == null ? "" : completedAt;
            progressPath = progressPath == null ? "" : progressPath;
            batchReportPath = batchReportPath == null ? "" : batchReportPath;
        }
    }

    public record ReleaseGateDecision(ReleaseGateStatus status,
                                      String runId,
                                      String datasetVersion,
                                      String modelName,
                                      String promptVersion,
                                      String promptHash,
                                      MemoryAdvisorPromptRegistry.PromptStatus promptStatus,
                                      String approvalActionRequired,
                                      int evaluatedCases,
                                      boolean qualityGatePassed,
                                      List<String> blockReasons,
                                      String generatedAt,
                                      String progressPath,
                                      String batchReportPath) {
        public ReleaseGateDecision {
            status = status == null ? ReleaseGateStatus.BLOCKED : status;
            runId = runId == null ? "" : runId;
            datasetVersion = datasetVersion == null ? "" : datasetVersion;
            modelName = modelName == null ? "" : modelName;
            promptVersion = promptVersion == null ? "" : promptVersion;
            promptHash = promptHash == null ? "" : promptHash;
            approvalActionRequired = approvalActionRequired == null ? "" : approvalActionRequired;
            evaluatedCases = Math.max(0, evaluatedCases);
            blockReasons = blockReasons == null ? List.of() : List.copyOf(blockReasons);
            generatedAt = generatedAt == null ? "" : generatedAt;
            progressPath = progressPath == null ? "" : progressPath;
            batchReportPath = batchReportPath == null ? "" : batchReportPath;
        }
    }
}
