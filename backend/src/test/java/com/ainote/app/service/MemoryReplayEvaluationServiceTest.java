package com.ainote.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryReplayEvaluationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MemoryReplayEvaluationService evaluator = new MemoryReplayEvaluationService(
            new MemoryCapturePolicy(),
            new MemoryCandidateExtractor());
    private static final int MIN_TOTAL_CASES = 200;
    private static final int MIN_CHINESE_CASES = 120;
    private static final int MIN_ALLOW_CASES = 70;
    private static final int MIN_DENY_CASES = 70;
    private static final Pattern CASE_ID_PATTERN =
            Pattern.compile("^replay_(cn|en)_[a-z]+(?:_[a-z]+)*_[a-z0-9]+(?:_[a-z0-9]+)*$");
    private static final Pattern CJK_PATTERN = Pattern.compile("\\p{IsHan}");
    private static final Set<String> VALID_DECISION_TYPES = EnumSet.allOf(MemoryCapturePolicy.DecisionType.class)
            .stream()
            .map(Enum::name)
            .collect(java.util.stream.Collectors.toSet());
    private static final Map<String, Integer> CATEGORY_MINIMUMS = categoryMinimums();

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
                        "replay_sensitive_secret",
                        "replay_cn_delete_all_notes",
                        "replay_cn_confirm_can",
                        "replay_cn_confirm_ok",
                        "replay_cn_explicit_preference",
                        "replay_cn_stable_style",
                        "replay_cn_style_correction",
                        "replay_cn_project_context",
                        "replay_cn_selected_note_reference",
                        "replay_cn_rag_reference_profile",
                        "replay_cn_one_off_reply_style",
                        "replay_cn_positive_feedback",
                        "replay_cn_sensitive_secret",
                        "replay_cn_forget_request");

        assertThat(cases).hasSizeGreaterThanOrEqualTo(25);

        MemoryReplayEvaluationService.EvaluationReport report = evaluator.evaluate(cases);
        assertThat(report.totalCases()).isGreaterThanOrEqualTo(12);
        assertThat(report.shouldNotRememberPrecision()).isEqualTo(1.0);
        assertThat(report.shouldRememberRecall()).isEqualTo(1.0);
        assertThat(report.candidateTypeAccuracy()).isEqualTo(1.0);
        assertThat(report.reasonCoverage()).isEqualTo(1.0);
        assertThat(report.signalCoverage()).isEqualTo(1.0);
        assertThat(report.failures()).isEmpty();
    }

    @Test
    void replayDatasetMeetsEnterpriseSeedShapeGate() throws Exception {
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases = loadCases();
        Set<String> ids = new HashSet<>();
        Set<String> userMessages = new HashSet<>();
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();

        for (MemoryReplayEvaluationService.MemoryReplayCase replayCase : cases) {
            assertThat(replayCase.id()).as("id").isNotBlank();
            assertThat(replayCase.id()).as(replayCase.id()).matches(CASE_ID_PATTERN);
            assertThat(ids.add(replayCase.id())).as("duplicate id " + replayCase.id()).isTrue();
            assertThat(replayCase.userMessage()).as(replayCase.id()).isNotBlank();
            assertThat(userMessages.add(replayCase.userMessage()))
                    .as("duplicate userMessage " + replayCase.userMessage())
                    .isTrue();
            assertThat(replayCase.assistantOutput()).as(replayCase.id()).isNotBlank();
            assertThat(VALID_DECISION_TYPES).as(replayCase.id()).contains(replayCase.expectedDecisionType());
            assertThat(replayCase.expectedPolicyReason()).as(replayCase.id()).isNotBlank();

            String category = categoryOf(replayCase.id());
            assertThat(category).as(replayCase.id()).isNotBlank();
            categoryCounts.merge(category, 1, Integer::sum);

            if (replayCase.id().startsWith("replay_cn_")) {
                assertThat(CJK_PATTERN.matcher(replayCase.userMessage()).find())
                        .as(replayCase.id() + " should contain CJK text")
                        .isTrue();
            }

            if ("ambiguous".equals(category)
                    && "no_stable_user_memory_signal".equals(replayCase.expectedPolicyReason())) {
                assertThat(replayCase.expectedSignals()).as(replayCase.id()).isEmpty();
            } else {
                assertThat(replayCase.expectedSignals()).as(replayCase.id()).isNotEmpty();
                assertThat(replayCase.expectedSignals()).as(replayCase.id()).allSatisfy(signal ->
                        assertThat(signal).isNotBlank());
            }

            if (replayCase.expectedCaptureAllowed()) {
                assertThat(replayCase.expectedMemoryType()).as(replayCase.id()).isNotBlank();
                assertThat(replayCase.expectedCorrection()).as(replayCase.id()).isNotNull();
            } else {
                assertThat(replayCase.expectedMemoryType()).as(replayCase.id()).isBlank();
                assertThat(replayCase.expectedCorrection()).as(replayCase.id()).isNull();
            }
        }

        assertThat(cases).hasSizeGreaterThanOrEqualTo(MIN_TOTAL_CASES);
        assertThat(cases.stream().filter(MemoryReplayEvaluationServiceTest::isChineseCase).count())
                .isGreaterThanOrEqualTo(MIN_CHINESE_CASES);
        assertThat(cases.stream().filter(MemoryReplayEvaluationService.MemoryReplayCase::expectedCaptureAllowed).count())
                .isGreaterThanOrEqualTo(MIN_ALLOW_CASES);
        assertThat(cases.stream().filter(replayCase -> !replayCase.expectedCaptureAllowed()).count())
                .isGreaterThanOrEqualTo(MIN_DENY_CASES);

        CATEGORY_MINIMUMS.forEach((category, minimum) ->
                assertThat(categoryCounts.getOrDefault(category, 0))
                        .as(category)
                        .isGreaterThanOrEqualTo(minimum));
    }

    private static Map<String, Integer> categoryMinimums() {
        Map<String, Integer> minimums = new LinkedHashMap<>();
        minimums.put("operation", 15);
        minimums.put("explicit_preference", 15);
        minimums.put("implicit_preference", 18);
        minimums.put("style", 18);
        minimums.put("correction", 14);
        minimums.put("project_context", 14);
        minimums.put("reference_only", 15);
        minimums.put("rag_reference", 15);
        minimums.put("one_off", 14);
        minimums.put("assistant_feedback", 10);
        minimums.put("sensitive", 10);
        minimums.put("forget", 10);
        minimums.put("ambiguous", 12);
        minimums.put("multi_turn_correction", 10);
        minimums.put("complex_project_context", 10);
        return minimums;
    }

    private static boolean isChineseCase(MemoryReplayEvaluationService.MemoryReplayCase replayCase) {
        return replayCase.id().startsWith("replay_cn_");
    }

    private static String categoryOf(String id) {
        String withoutLanguage = id == null ? "" : id.replaceFirst("^replay_(cn|en)_", "");
        for (String category : CATEGORY_MINIMUMS.keySet()) {
            if (withoutLanguage.startsWith(category + "_")) {
                return category;
            }
        }
        return "";
    }

    private List<MemoryReplayEvaluationService.MemoryReplayCase> loadCases() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/memory/replay-eval-cases.json")) {
            assertThat(input).isNotNull();
            return objectMapper.readValue(input, new TypeReference<>() {});
        }
    }
}
