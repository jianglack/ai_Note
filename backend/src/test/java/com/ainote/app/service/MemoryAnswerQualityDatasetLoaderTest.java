package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryAnswerQualityDatasetLoaderTest {

    @Test
    void datasetHas120ReviewedTraceableCasesWithBalancedCoverage() {
        List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> cases =
                MemoryAnswerQualityDatasetLoader.load();

        assertThat(cases).hasSize(120);
        assertThat(cases).extracting(MemoryAnswerQualityEvaluationService.AnswerQualityCase::id)
                .doesNotHaveDuplicates();
        for (MemoryAnswerQualityEvaluationService.ScenarioType type
                : MemoryAnswerQualityEvaluationService.ScenarioType.values()) {
            assertThat(cases.stream().filter(value -> value.scenarioType() == type)).hasSize(20);
        }
        assertThat(cases).allSatisfy(value -> {
            assertThat(value.query()).isNotBlank();
            assertThat(value.provenance().reviewStatus()).isEqualTo("approved");
            assertThat(value.provenance().sourceReference()).isNotBlank();
            assertThat(value.treatmentContext()).contains("<context_policy>");
        });
    }

    @Test
    void datasetKeepsRealHumanSourcesDistinctFromReviewedPerturbations() {
        var cases = MemoryAnswerQualityDatasetLoader.load();

        assertThat(cases.stream().filter(value ->
                "external_real_human_anonymized".equals(value.provenance().sourceType())))
                .hasSize(8);
        assertThat(cases.stream().filter(value ->
                value.provenance().sourceType().startsWith("reviewed_adversarial")))
                .hasSize(112);
    }
}
