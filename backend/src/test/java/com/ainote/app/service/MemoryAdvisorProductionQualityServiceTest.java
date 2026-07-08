package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryAdvisorProductionQualityServiceTest {

    private final MemoryAdvisorProductionQualityService service = new MemoryAdvisorProductionQualityService(
            new MemoryAdvisorReplayEvaluationService(),
            new MemoryReplayEvaluationService(new MemoryCapturePolicy(), new MemoryCandidateExtractor()));

    @Test
    void oracleAdvisorPassesProductionReadinessGateOnActiveReplayDataset() {
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases = loadCases();
        MemorySignalAdvisor advisor = oracleAdvisor(cases);

        MemoryAdvisorProductionQualityService.AdvisorReadinessReport report = service.evaluate(
                cases,
                advisor,
                productionRun());

        assertThat(report.qualityGatePassed()).isTrue();
        assertThat(report.gateFailures()).isEmpty();
        assertThat(report.run().runId()).isEqualTo("advisor-prod-gate-2026-07-08");
        assertThat(report.run().advisorName()).isEqualTo("llm-memory-advisor");
        assertThat(report.run().modelName()).isEqualTo("offline-oracle");
        assertThat(report.run().promptVersion()).isEqualTo(LlmMemorySignalAdvisor.PROMPT_VERSION);
        assertThat(report.run().datasetVersion()).isEqualTo("active-memory-replay-v2");
        assertThat(report.metrics().totalCases()).isGreaterThanOrEqualTo(4000);
        assertThat(report.metrics().baselineDecisionAccuracy()).isEqualTo(1.0);
        assertThat(report.metrics().advisorDecisionAccuracyDeltaVsBaseline()).isEqualTo(0.0);
        assertThat(report.metrics().sensitiveFalseAllowRate()).isEqualTo(0.0);
        assertThat(report.metrics().p95LatencyMillis()).isGreaterThanOrEqualTo(0);
        assertThat(report.failureReport().totalFailures()).isZero();
        assertThat(report.failureReport().samples()).isEmpty();
    }

    @Test
    void degradedAdvisorFailsProductionGateAndProducesCappedFailureReport() {
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases = loadCases();
        MemorySignalAdvisor degradedAdvisor = request -> MemorySignalAdvisor.AdvisorResult.capture(
                "preference",
                0.99,
                List.of("advisor_preference_signal"),
                "captures everything");

        MemoryAdvisorProductionQualityService.AdvisorReadinessReport report = service.evaluate(
                cases,
                degradedAdvisor,
                productionRun());

        assertThat(report.qualityGatePassed()).isFalse();
        assertThat(report.gateFailures())
                .extracting(MemoryAdvisorProductionQualityService.QualityGateFailure::gate)
                .contains(
                        "false_positive_rate",
                        "sensitive_false_allow_rate",
                        "decision_accuracy_delta_vs_baseline");
        assertThat(report.metrics().falsePositiveRate()).isGreaterThan(0.0);
        assertThat(report.metrics().sensitiveFalseAllowRate()).isGreaterThan(0.0);
        assertThat(report.failureReport().totalFailures()).isGreaterThan(50);
        assertThat(report.failureReport().samples()).hasSize(50);
        assertThat(report.failureReport().samples())
                .anySatisfy(sample -> assertThat(sample.sensitiveBoundary()).isTrue());
    }

    private MemoryAdvisorProductionQualityService.AdvisorEvaluationRun productionRun() {
        return MemoryAdvisorProductionQualityService.AdvisorEvaluationRun.production(
                "advisor-prod-gate-2026-07-08",
                "llm-memory-advisor",
                "offline-oracle",
                LlmMemorySignalAdvisor.PROMPT_VERSION,
                "active-memory-replay-v2");
    }

    private MemorySignalAdvisor oracleAdvisor(List<MemoryReplayEvaluationService.MemoryReplayCase> cases) {
        Map<String, MemoryReplayEvaluationService.MemoryReplayCase> expectedByMessage = cases.stream()
                .collect(Collectors.toMap(
                        MemoryReplayEvaluationService.MemoryReplayCase::userMessage,
                        Function.identity(),
                        (left, right) -> left));
        return request -> {
            MemoryReplayEvaluationService.MemoryReplayCase expected = expectedByMessage.get(request.userMessage());
            if (expected == null || !expected.expectedCaptureAllowed()) {
                return MemorySignalAdvisor.AdvisorResult.noCapture("none", 0.1, List.of(), "not stable memory");
            }
            return MemorySignalAdvisor.AdvisorResult.capture(
                    expected.expectedMemoryType(),
                    0.99,
                    List.of(advisorSignal(expected.expectedMemoryType())),
                    "oracle");
        };
    }

    private String advisorSignal(String memoryType) {
        return switch (memoryType) {
            case "project_context" -> "advisor_project_context_signal";
            case "style" -> "advisor_interaction_style_signal";
            default -> "advisor_preference_signal";
        };
    }

    private List<MemoryReplayEvaluationService.MemoryReplayCase> loadCases() {
        return MemoryReplayDatasetLoader.loadActiveDataset().cases();
    }
}
