package com.ainote.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemorySystemEvaluationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MemoryCapturePolicy policy = new MemoryCapturePolicy();
    private final MemoryCandidateExtractor extractor = new MemoryCandidateExtractor();

    @Test
    void evalDatasetCoversRequiredGovernanceScenarios() throws Exception {
        List<MemoryEvalCase> cases = loadCases();

        assertThat(cases)
                .extracting(MemoryEvalCase::id)
                .contains(
                        "should_not_remember_delete_all_notes",
                        "should_not_remember_confirm",
                        "should_not_remember_cancel",
                        "should_remember_explicit_preference",
                        "should_supersede_corrected_preference",
                        "should_capture_interaction_style_correction",
                        "should_capture_stable_style_preference",
                        "should_capture_explicit_project_context",
                        "should_not_capture_one_off_style_instruction",
                        "should_not_remember_selected_note_summary",
                        "should_not_capture_reference_note_even_with_preference_words",
                        "should_not_remember_rag_reference_as_profile"
                );
    }

    @Test
    void capturePolicyMatchesEvalDatasetWithShouldNotRememberAtOneHundredPercent() throws Exception {
        List<String> failures = loadCases().stream()
                .map(this::evaluatePolicy)
                .filter(result -> !result.passed())
                .map(EvalResult::message)
                .toList();

        assertThat(failures).isEmpty();
    }

    @Test
    void allowedEvalCasesProduceExpectedMemoryCandidates() throws Exception {
        for (MemoryEvalCase evalCase : loadCases()) {
            if (!evalCase.expectedCaptureAllowed()) {
                continue;
            }

            MemoryCapturePolicy.CaptureRequest request = request(evalCase);
            MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(request);
            List<MemoryCandidateExtractor.MemoryCandidate> candidates = extractor.extract(request, decision);

            assertThat(candidates)
                    .as(evalCase.id())
                    .hasSize(1);
            assertThat(candidates.get(0).memoryType())
                    .as(evalCase.id())
                    .isEqualTo(evalCase.expectedMemoryType());
            assertThat(candidates.get(0).correction())
                    .as(evalCase.id())
                    .isEqualTo(Boolean.TRUE.equals(evalCase.expectedCorrection()));
        }
    }

    private EvalResult evaluatePolicy(MemoryEvalCase evalCase) {
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(request(evalCase));
        boolean passed = decision.allowed() == evalCase.expectedCaptureAllowed()
                && decision.type().name().equals(evalCase.expectedDecisionType());
        return new EvalResult(
                passed,
                evalCase.id() + " expected allowed=" + evalCase.expectedCaptureAllowed()
                        + " type=" + evalCase.expectedDecisionType()
                        + " but got allowed=" + decision.allowed()
                        + " type=" + decision.type()
                        + " reason=" + decision.reason()
        );
    }

    private MemoryCapturePolicy.CaptureRequest request(MemoryEvalCase evalCase) {
        return new MemoryCapturePolicy.CaptureRequest(
                "user-1",
                evalCase.userMessage(),
                evalCase.assistantOutput());
    }

    private List<MemoryEvalCase> loadCases() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/memory/eval-cases.json")) {
            assertThat(input).isNotNull();
            return objectMapper.readValue(input, new TypeReference<>() {});
        }
    }

    record MemoryEvalCase(
            String id,
            String userMessage,
            String assistantOutput,
            boolean expectedCaptureAllowed,
            String expectedDecisionType,
            String expectedMemoryType,
            Boolean expectedCorrection
    ) {
    }

    record EvalResult(boolean passed, String message) {
    }
}
