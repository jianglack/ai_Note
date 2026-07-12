package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryAdvisorFormalEvaluationCaseSelectorTest {

    @Test
    void exactIdsPreserveRequestedOrderAndDeduplicate() {
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases = List.of(
                replay("case-a"),
                replay("case-b"),
                replay("case-c"));

        List<MemoryReplayEvaluationService.MemoryReplayCase> selected =
                MemoryAdvisorFormalEvaluationCaseSelector.select(
                        cases,
                        new MemoryAdvisorFormalEvaluationCaseSelector.SelectionConfig(
                                "first",
                                List.of("case-c", "case-a", "case-c"),
                                100));

        assertThat(selected)
                .extracting(MemoryReplayEvaluationService.MemoryReplayCase::id)
                .containsExactly("case-c", "case-a");
    }

    @Test
    void exactIdsRejectMissingCases() {
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases = List.of(replay("case-a"));

        assertThatThrownBy(() -> MemoryAdvisorFormalEvaluationCaseSelector.select(
                cases,
                new MemoryAdvisorFormalEvaluationCaseSelector.SelectionConfig(
                        "first",
                        List.of("case-missing"),
                        100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("case-missing");
    }

    @Test
    void stratifiedSelectionIsDeterministicAndIncludesSourceTypes() {
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases = List.of(
                replay("replay_en_explicit_preference_seed"),
                replay("replay_en_explicit_preference_sim_0001"),
                replay("replay_en_explicit_preference_external_0001"),
                replay("replay_cn_implicit_preference_seed"),
                replay("replay_cn_implicit_preference_sim_0001"),
                replay("replay_cn_implicit_preference_external_0001"),
                replay("replay_en_sensitive_api_key_seed"),
                replay("replay_en_sensitive_api_key_sim_0001"),
                replay("replay_en_sensitive_api_key_external_0001"));

        MemoryAdvisorFormalEvaluationCaseSelector.SelectionConfig config =
                new MemoryAdvisorFormalEvaluationCaseSelector.SelectionConfig("stratified", List.of(), 6);

        List<MemoryReplayEvaluationService.MemoryReplayCase> selected =
                MemoryAdvisorFormalEvaluationCaseSelector.select(cases, config);
        List<MemoryReplayEvaluationService.MemoryReplayCase> selectedAgain =
                MemoryAdvisorFormalEvaluationCaseSelector.select(cases, config);

        assertThat(selected)
                .extracting(MemoryReplayEvaluationService.MemoryReplayCase::id)
                .containsExactlyElementsOf(selectedAgain.stream()
                        .map(MemoryReplayEvaluationService.MemoryReplayCase::id)
                        .toList());
        assertThat(selected).hasSize(6);
        assertThat(selected)
                .extracting(MemoryReplayEvaluationService.MemoryReplayCase::id)
                .anyMatch(id -> id.contains("_external_"))
                .anyMatch(id -> id.contains("_sim_"))
                .anyMatch(id -> !id.contains("_external_") && !id.contains("_sim_"));
    }

    @Test
    void firstSelectionLeavesOrderForBatchService() {
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases = List.of(
                replay("case-a"),
                replay("case-b"));

        List<MemoryReplayEvaluationService.MemoryReplayCase> selected =
                MemoryAdvisorFormalEvaluationCaseSelector.select(
                        cases,
                        new MemoryAdvisorFormalEvaluationCaseSelector.SelectionConfig(
                                "first",
                                List.of(),
                                1));

        assertThat(selected)
                .extracting(MemoryReplayEvaluationService.MemoryReplayCase::id)
                .containsExactly("case-a", "case-b");
    }

    @Test
    void fix1FailureRegressionCaseIdsResolveAgainstActiveDataset() throws Exception {
        List<String> caseIds = fix1FailureCaseIds();

        List<MemoryReplayEvaluationService.MemoryReplayCase> selected =
                MemoryAdvisorFormalEvaluationCaseSelector.select(
                        MemoryReplayDatasetLoader.loadActiveDataset().cases(),
                        new MemoryAdvisorFormalEvaluationCaseSelector.SelectionConfig("first", caseIds, 100));

        assertThat(caseIds).hasSize(34);
        assertThat(selected)
                .extracting(MemoryReplayEvaluationService.MemoryReplayCase::id)
                .containsExactlyElementsOf(caseIds);
    }

    private static List<String> fix1FailureCaseIds() throws Exception {
        try (InputStream input = MemoryAdvisorFormalEvaluationCaseSelectorTest.class.getResourceAsStream(
                "/memory/advisor-v2-fix1-failure-case-ids.txt")) {
            assertThat(input).isNotNull();
            return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .toList();
        }
    }

    private static MemoryReplayEvaluationService.MemoryReplayCase replay(String id) {
        return new MemoryReplayEvaluationService.MemoryReplayCase(
                id,
                "message " + id,
                "assistant",
                false,
                "DENY_TRANSIENT",
                "",
                false,
                "no_stable_user_memory_signal",
                List.of());
    }
}
