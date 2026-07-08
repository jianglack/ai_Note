package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryAdvisorReplayEvaluationServiceTest {

    private final MemoryAdvisorReplayEvaluationService evaluator = new MemoryAdvisorReplayEvaluationService();

    @Test
    void evaluatesAdvisorQualityMetrics() {
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases = List.of(
                replayCase("remember_style", "记住，我希望以后回答都先给结论。", true, "preference"),
                replayCase("selected_note", "总结这篇笔记：里面说我喜欢红色。", false, null),
                replayCase("project_context", "项目上下文：这是 AI 笔记系统。", true, "project_context"));
        MemorySignalAdvisor advisor = request -> {
            if (request.userMessage().contains("项目上下文")) {
                return MemorySignalAdvisor.AdvisorResult.capture(
                        "project_context", 0.92, List.of("advisor_project_context_signal"), "project context");
            }
            if (request.userMessage().contains("记住")) {
                return MemorySignalAdvisor.AdvisorResult.capture(
                        "preference", 0.91, List.of("advisor_preference_signal"), "explicit preference");
            }
            return MemorySignalAdvisor.AdvisorResult.noCapture("none", 0.2, List.of(), "reference only");
        };

        MemoryAdvisorReplayEvaluationService.AdvisorEvaluationReport report = evaluator.evaluate(cases, advisor);

        assertThat(report.totalCases()).isEqualTo(3);
        assertThat(report.availabilityRate()).isEqualTo(1.0);
        assertThat(report.captureDecisionAccuracy()).isEqualTo(1.0);
        assertThat(report.falsePositiveRate()).isEqualTo(0.0);
        assertThat(report.falseNegativeRate()).isEqualTo(0.0);
        assertThat(report.memoryTypeAccuracy()).isEqualTo(1.0);
        assertThat(report.failures()).isEmpty();
    }

    @Test
    void reportsFalsePositiveFalseNegativeWrongTypeAndUnavailable() {
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases = List.of(
                replayCase("false_positive", "总结这篇笔记：里面说我喜欢红色。", false, null),
                replayCase("false_negative", "记住，我希望以后回答都先给结论。", true, "preference"),
                replayCase("wrong_type", "项目上下文：这是 AI 笔记系统。", true, "project_context"),
                replayCase("unavailable", "我希望以后默认用中文回答。", true, "preference"));
        MemorySignalAdvisor advisor = request -> {
            if (request.userMessage().contains("总结这篇笔记")) {
                return MemorySignalAdvisor.AdvisorResult.capture(
                        "preference", 0.9, List.of("advisor_preference_signal"), "wrong capture");
            }
            if (request.userMessage().contains("记住")) {
                return MemorySignalAdvisor.AdvisorResult.noCapture("none", 0.2, List.of(), "missed");
            }
            if (request.userMessage().contains("项目上下文")) {
                return MemorySignalAdvisor.AdvisorResult.capture(
                        "preference", 0.9, List.of("advisor_preference_signal"), "wrong type");
            }
            return MemorySignalAdvisor.AdvisorResult.unavailable(List.of("advisor_failed"), "failed");
        };

        MemoryAdvisorReplayEvaluationService.AdvisorEvaluationReport report = evaluator.evaluate(cases, advisor);

        assertThat(report.availabilityRate()).isEqualTo(0.75);
        assertThat(report.captureDecisionAccuracy()).isEqualTo(0.25);
        assertThat(report.falsePositiveRate()).isEqualTo(1.0);
        assertThat(report.falseNegativeRate()).isEqualTo(2.0 / 3.0);
        assertThat(report.memoryTypeAccuracy()).isEqualTo(0.0);
        assertThat(report.failures())
                .extracting(MemoryAdvisorReplayEvaluationService.AdvisorCaseResult::id)
                .containsExactly("false_positive", "false_negative", "wrong_type", "unavailable");
    }

    @Test
    void replayDatasetCanBeUsedAsAdvisorEvalSet() throws Exception {
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases = loadCases();
        Map<String, MemoryReplayEvaluationService.MemoryReplayCase> expectedByMessage = cases.stream()
                .collect(Collectors.toMap(
                        MemoryReplayEvaluationService.MemoryReplayCase::userMessage,
                        Function.identity(),
                        (left, right) -> left));
        assertThat(expectedByMessage).hasSize(cases.size());
        MemorySignalAdvisor oracleAdvisor = request -> {
            MemoryReplayEvaluationService.MemoryReplayCase expected = expectedByMessage.get(request.userMessage());
            if (expected == null || !expected.expectedCaptureAllowed()) {
                return MemorySignalAdvisor.AdvisorResult.noCapture("none", 0.1, List.of(), "not stable memory");
            }
            return MemorySignalAdvisor.AdvisorResult.capture(
                    expected.expectedMemoryType(),
                    0.95,
                    List.of(advisorSignal(expected.expectedMemoryType())),
                    "oracle");
        };

        MemoryAdvisorReplayEvaluationService.AdvisorEvaluationReport report = evaluator.evaluate(cases, oracleAdvisor);

        assertThat(report.totalCases()).isGreaterThanOrEqualTo(2000);
        assertThat(report.availabilityRate()).isEqualTo(1.0);
        assertThat(report.captureDecisionAccuracy()).isEqualTo(1.0);
        assertThat(report.falsePositiveRate()).isEqualTo(0.0);
        assertThat(report.falseNegativeRate()).isEqualTo(0.0);
        assertThat(report.memoryTypeAccuracy()).isEqualTo(1.0);
        assertThat(report.failures()).isEmpty();
    }

    private MemoryReplayEvaluationService.MemoryReplayCase replayCase(String id,
                                                                      String userMessage,
                                                                      boolean expectedCaptureAllowed,
                                                                      String expectedMemoryType) {
        return new MemoryReplayEvaluationService.MemoryReplayCase(
                id,
                userMessage,
                "ok",
                expectedCaptureAllowed,
                expectedCaptureAllowed ? "ALLOW_IMPLICIT_LOW_CONFIDENCE" : "DENY_TRANSIENT",
                expectedMemoryType,
                false,
                "",
                List.of());
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
