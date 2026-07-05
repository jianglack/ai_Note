package com.ainote.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryReplayEvaluationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MemoryReplayEvaluationService evaluator = new MemoryReplayEvaluationService(
            new MemoryCapturePolicy(),
            new MemoryCandidateExtractor());

    @Test
    void evaluatesReplayCasesWithGovernanceMetrics() {
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases = List.of(
                new MemoryReplayEvaluationService.MemoryReplayCase(
                        "explicit_preference",
                        "remember: I prefer concise answers.",
                        "Noted.",
                        true,
                        "ALLOW_EXPLICIT",
                        "preference",
                        false,
                        "explicit_memory",
                        List.of("explicit_remember")),
                new MemoryReplayEvaluationService.MemoryReplayCase(
                        "positive_feedback_not_memory",
                        "I like this answer, thanks.",
                        "Glad it helped.",
                        false,
                        "DENY_TRANSIENT",
                        null,
                        null,
                        "assistant_feedback",
                        List.of("assistant_feedback")),
                new MemoryReplayEvaluationService.MemoryReplayCase(
                        "project_context",
                        "Project context: This project is an AI note system.",
                        "Recorded.",
                        true,
                        "ALLOW_IMPLICIT_LOW_CONFIDENCE",
                        "project_context",
                        false,
                        "project_context",
                        List.of("project_context")));

        MemoryReplayEvaluationService.EvaluationReport report = evaluator.evaluate(cases);

        assertThat(report.totalCases()).isEqualTo(3);
        assertThat(report.decisionAccuracy()).isEqualTo(1.0);
        assertThat(report.shouldNotRememberPrecision()).isEqualTo(1.0);
        assertThat(report.shouldRememberRecall()).isEqualTo(1.0);
        assertThat(report.candidateTypeAccuracy()).isEqualTo(1.0);
        assertThat(report.correctionAccuracy()).isEqualTo(1.0);
        assertThat(report.reasonCoverage()).isEqualTo(1.0);
        assertThat(report.signalCoverage()).isEqualTo(1.0);
        assertThat(report.failures()).isEmpty();
    }

    @Test
    void reportsPerCaseFailuresWhenExpectationsDoNotMatch() {
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases = List.of(
                new MemoryReplayEvaluationService.MemoryReplayCase(
                        "wrong_expectation",
                        "remember: I prefer concise answers.",
                        "Noted.",
                        false,
                        "DENY_TRANSIENT",
                        null,
                        null,
                        "assistant_feedback",
                        List.of("assistant_feedback")));

        MemoryReplayEvaluationService.EvaluationReport report = evaluator.evaluate(cases);

        assertThat(report.decisionAccuracy()).isEqualTo(0.0);
        assertThat(report.failures())
                .hasSize(1)
                .first()
                .satisfies(failure -> {
                    assertThat(failure.id()).isEqualTo("wrong_expectation");
                    assertThat(failure.messages()).anyMatch(message -> message.contains("allowed"));
                    assertThat(failure.messages()).anyMatch(message -> message.contains("decisionType"));
                });
    }

    @Test
    void replayDatasetMeetsL3MemoryGovernanceGates() throws Exception {
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases = loadCases();

        assertThat(cases)
                .extracting(MemoryReplayEvaluationService.MemoryReplayCase::id)
                .contains(
                        "replay_delete_all_notes",
                        "replay_confirm",
                        "replay_cancel",
                        "replay_explicit_preference",
                        "replay_implicit_preference",
                        "replay_preference_correction",
                        "replay_project_context",
                        "replay_selected_note_reference",
                        "replay_rag_reference_profile",
                        "replay_one_off_reply_style",
                        "replay_positive_feedback",
                        "replay_sensitive_secret");

        MemoryReplayEvaluationService.EvaluationReport report = evaluator.evaluate(cases);

        assertThat(report.totalCases()).isGreaterThanOrEqualTo(12);
        assertThat(report.shouldNotRememberPrecision()).isEqualTo(1.0);
        assertThat(report.shouldRememberRecall()).isEqualTo(1.0);
        assertThat(report.candidateTypeAccuracy()).isEqualTo(1.0);
        assertThat(report.reasonCoverage()).isEqualTo(1.0);
        assertThat(report.signalCoverage()).isEqualTo(1.0);
        assertThat(report.failures()).isEmpty();
    }

    private List<MemoryReplayEvaluationService.MemoryReplayCase> loadCases() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/memory/replay-eval-cases.json")) {
            assertThat(input).isNotNull();
            return objectMapper.readValue(input, new TypeReference<>() {});
        }
    }
}
