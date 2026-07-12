package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryAdvisorReleaseGateServiceTest {

    @Test
    void staleV1PromptBlocksRelease() {
        MemoryAdvisorReleaseGateService service = service(168);
        MemoryAdvisorReleaseGateService.ReleaseGateDecision decision = service.evaluate(packageWithPrompt("memory-advisor-v1"));

        assertThat(decision.status()).isEqualTo(MemoryAdvisorReleaseGateService.ReleaseGateStatus.BLOCKED);
        assertThat(decision.blockReasons()).contains("prompt_version_unknown");
    }

    @Test
    void bareReadinessReportBlocksRelease() {
        MemoryAdvisorReleaseGateService service = service(168);
        MemoryAdvisorReleaseGateService.ReleaseGateDecision decision = service.evaluateReadinessOnly(readinessPassing());

        assertThat(decision.status()).isEqualTo(MemoryAdvisorReleaseGateService.ReleaseGateStatus.BLOCKED);
        assertThat(decision.blockReasons()).contains("missing_release_readiness_package");
    }

    @Test
    void candidatePromptCanPassWithApprovalActionRequired() {
        MemoryAdvisorReleaseGateService service = service(168);
        MemoryAdvisorReleaseGateService.ReleaseGateDecision decision = service.evaluate(completePassingPackage());

        assertThat(decision.status()).isEqualTo(MemoryAdvisorReleaseGateService.ReleaseGateStatus.PASS);
        assertThat(decision.promptStatus()).isEqualTo(MemoryAdvisorPromptRegistry.PromptStatus.CANDIDATE);
        assertThat(decision.approvalActionRequired()).isEqualTo("promote_prompt_to_approved");
    }

    @Test
    void staleReportBlocksReleaseReadiness() {
        MemoryAdvisorReleaseGateService service = service(168);
        MemoryAdvisorReleaseGateService.MemoryAdvisorReleaseReadinessPackage releasePackage =
                completePassingPackageWithCompletedAt("2020-01-01T00:00:00Z");

        MemoryAdvisorReleaseGateService.ReleaseGateDecision decision = service.evaluate(releasePackage);

        assertThat(decision.status()).isEqualTo(MemoryAdvisorReleaseGateService.ReleaseGateStatus.BLOCKED);
        assertThat(decision.blockReasons()).contains("report_stale");
    }

    @Test
    void guardedAdvisorCanPassWhenRawThresholdIsDiagnosticOnly() {
        MemoryAdvisorReleaseGateService service = service(168);
        MemoryAdvisorReleaseGateService.MemoryAdvisorReleaseReadinessPackage releasePackage =
                packageWithCalibration(calibrationRawThresholdNotDeployable());

        MemoryAdvisorReleaseGateService.ReleaseGateDecision decision = service.evaluate(releasePackage);

        assertThat(decision.status()).isEqualTo(MemoryAdvisorReleaseGateService.ReleaseGateStatus.PASS);
        assertThat(decision.blockReasons()).doesNotContain("no_deployable_threshold");
    }

    private static MemoryAdvisorReleaseGateService service(long maxReportAgeHours) {
        return new MemoryAdvisorReleaseGateService(new MemoryAdvisorPromptRegistry(), maxReportAgeHours);
    }

    private static MemoryAdvisorReleaseGateService.MemoryAdvisorReleaseReadinessPackage completePassingPackage() {
        return completePassingPackageWithCompletedAt(Instant.now().toString());
    }

    private static MemoryAdvisorReleaseGateService.MemoryAdvisorReleaseReadinessPackage completePassingPackageWithCompletedAt(
            String completedAt) {
        return packageWithPrompt(LlmMemorySignalAdvisor.PROMPT_VERSION, completedAt);
    }

    private static MemoryAdvisorReleaseGateService.MemoryAdvisorReleaseReadinessPackage packageWithPrompt(
            String promptVersion) {
        return packageWithPrompt(promptVersion, Instant.now().toString());
    }

    private static MemoryAdvisorReleaseGateService.MemoryAdvisorReleaseReadinessPackage packageWithPrompt(
            String promptVersion,
            String completedAt) {
        return packageWithPromptAndCalibration(promptVersion, completedAt, calibrationPassing());
    }

    private static MemoryAdvisorReleaseGateService.MemoryAdvisorReleaseReadinessPackage packageWithCalibration(
            MemoryAdvisorCalibrationService.CalibrationReport calibrationReport) {
        return packageWithPromptAndCalibration(
                LlmMemorySignalAdvisor.PROMPT_VERSION,
                Instant.now().toString(),
                calibrationReport);
    }

    private static MemoryAdvisorReleaseGateService.MemoryAdvisorReleaseReadinessPackage packageWithPromptAndCalibration(
            String promptVersion,
            String completedAt,
            MemoryAdvisorCalibrationService.CalibrationReport calibrationReport) {
        MemoryAdvisorPromptRegistry registry = new MemoryAdvisorPromptRegistry();
        return new MemoryAdvisorReleaseGateService.MemoryAdvisorReleaseReadinessPackage(
                "release-run",
                "active-memory-replay-v2",
                "deepseek-chat",
                promptVersion,
                completedAt,
                "target/progress.jsonl",
                "target/batch.json",
                registry.find(promptVersion).orElse(null),
                readinessPassing(promptVersion),
                calibrationReport,
                abPassing(),
                failureMetricsPassing());
    }

    private static MemoryAdvisorProductionQualityService.AdvisorReadinessReport readinessPassing() {
        return readinessPassing(LlmMemorySignalAdvisor.PROMPT_VERSION);
    }

    private static MemoryAdvisorProductionQualityService.AdvisorReadinessReport readinessPassing(String promptVersion) {
        MemoryAdvisorProductionQualityService.AdvisorEvaluationRun run =
                MemoryAdvisorProductionQualityService.AdvisorEvaluationRun.production(
                        "release-run",
                        "llm-memory-advisor",
                        "deepseek-chat",
                        promptVersion,
                        "active-memory-replay-v2");
        MemoryAdvisorProductionQualityService.AdvisorQualityMetrics metrics =
                new MemoryAdvisorProductionQualityService.AdvisorQualityMetrics(
                        4000, 1.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 100);
        return new MemoryAdvisorProductionQualityService.AdvisorReadinessReport(
                run,
                metrics,
                true,
                List.of(),
                new MemoryAdvisorReplayEvaluationService.AdvisorEvaluationReport(
                        4000, 2000, 2000, 1.0, 1.0, 0.0, 0.0, 1.0, 100, List.of(), List.of()),
                new MemoryReplayEvaluationService.EvaluationReport(
                        4000, 2000, 2000, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, List.of(), List.of()),
                new MemoryAdvisorProductionQualityService.AdvisorFailureReport(
                        "release-run", "active-memory-replay-v2", "deepseek-chat", promptVersion, 0, List.of()));
    }

    private static MemoryAdvisorCalibrationService.CalibrationReport calibrationPassing() {
        return new MemoryAdvisorCalibrationService.CalibrationReport(
                4000,
                4000,
                2000,
                2000,
                0,
                0,
                0,
                0.82,
                0.50,
                true,
                "passing calibration",
                List.of(new MemoryAdvisorCalibrationService.ThresholdCandidate(
                        0.50, 1.0, 0.0, 0.0, 1.0, 0.0, true)));
    }

    private static MemoryAdvisorCalibrationService.CalibrationReport calibrationRawThresholdNotDeployable() {
        return new MemoryAdvisorCalibrationService.CalibrationReport(
                4000,
                4000,
                2000,
                2000,
                0,
                0,
                0,
                0.80,
                0.80,
                false,
                "raw llm threshold is diagnostic; final guarded advisor passed release metrics",
                List.of(new MemoryAdvisorCalibrationService.ThresholdCandidate(
                        0.80, 0.988, 0.0, 0.026, 0.973, 0.0, false)));
    }

    private static MemoryAdvisorAbComparisonService.AbComparisonReport abPassing() {
        return new MemoryAdvisorAbComparisonService.AbComparisonReport(
                4000,
                2000,
                2000,
                0,
                0,
                1.0,
                1.0,
                0.0,
                List.of(),
                List.of());
    }

    private static MemoryAdvisorFailureMetricsService.FailureMetricsReport failureMetricsPassing() {
        return new MemoryAdvisorFailureMetricsService.FailureMetricsReport(
                4000,
                4000,
                0,
                1.0,
                0,
                0,
                4000,
                0,
                100,
                List.of());
    }
}
