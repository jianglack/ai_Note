package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryAdvisorAbComparisonServiceTest {

    @Test
    void comparesAdvisorAgainstRulePolicyBaseline() {
        MemoryAdvisorAbComparisonService service = new MemoryAdvisorAbComparisonService(
                new MemoryCapturePolicy(),
                new MemoryCandidateExtractor());
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases = List.of(
                replayCase("allow", "remember: I prefer concise answers.", true, "ALLOW_EXPLICIT", "preference"),
                replayCase("deny", "summarize this for this reply only", false, "DENY_TRANSIENT", ""));
        List<MemoryAdvisorFormalBatchEvaluationService.ProgressEntry> progress = List.of(
                progress("allow", true, "preference"),
                progress("deny", true, "preference"));

        MemoryAdvisorAbComparisonService.AbComparisonReport report = service.compare(cases, progress);

        assertThat(report.totalCases()).isEqualTo(2);
        assertThat(report.bothAllow()).isEqualTo(1);
        assertThat(report.advisorOnlyAllow()).isEqualTo(1);
        assertThat(report.ruleOnlyAllow()).isZero();
        assertThat(report.advisorRegressions()).extracting(MemoryAdvisorAbComparisonService.AbCaseSample::caseId)
                .contains("deny");
    }

    private static MemoryAdvisorFormalBatchEvaluationService.ProgressEntry progress(String id,
                                                                                   boolean shouldCapture,
                                                                                   String memoryType) {
        MemorySignalAdvisor.AdvisorResult finalResult = shouldCapture
                ? MemorySignalAdvisor.AdvisorResult.capture(
                memoryType,
                0.9,
                List.of("advisor_preference_signal"),
                "advisor decision")
                : MemorySignalAdvisor.AdvisorResult.noCapture("none", 0.1, List.of(), "advisor reject");
        return new MemoryAdvisorFormalBatchEvaluationService.ProgressEntry(
                id,
                MemoryAdvisorFormalBatchEvaluationService.CaseStatus.COMPLETED,
                true,
                finalResult.shouldCapture(),
                finalResult.memoryType(),
                finalResult.confidence(),
                finalResult.signals(),
                finalResult.reason(),
                MemoryAdvisorRawResult.fromFinal(finalResult),
                10,
                "2026-01-01T00:00:00Z",
                1,
                List.of());
    }

    private static MemoryReplayEvaluationService.MemoryReplayCase replayCase(String id,
                                                                            String userMessage,
                                                                            boolean expectedAllowed,
                                                                            String expectedDecisionType,
                                                                            String expectedMemoryType) {
        return new MemoryReplayEvaluationService.MemoryReplayCase(
                id,
                userMessage,
                "ok",
                expectedAllowed,
                expectedDecisionType,
                expectedMemoryType,
                null,
                expectedAllowed ? "explicit_memory" : "one_off_instruction",
                List.of());
    }
}
