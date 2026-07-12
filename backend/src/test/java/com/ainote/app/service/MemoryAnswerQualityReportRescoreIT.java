package com.ainote.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryAnswerQualityReportRescoreIT {

    @Test
    @EnabledIfEnvironmentVariable(named = "MEMORY_ANSWER_QUALITY_RESCORE_ENABLED", matches = "true")
    void rescoresPersistedFormalAnswersWithoutCallingModels() {
        String modelName = value("MEMORY_ANSWER_QUALITY_MODEL", value("DEEPSEEK_MODEL", "deepseek-chat"));
        String judgeModelName = value("MEMORY_ANSWER_QUALITY_JUDGE_MODEL", modelName);
        Path source = Path.of(value(
                "MEMORY_ANSWER_QUALITY_RESCORE_SOURCE_REPORT",
                "target/memory-answer-quality-eval/memory-answer-quality-formal-eval-120-v3.json"));
        Path target = Path.of(value(
                "MEMORY_ANSWER_QUALITY_RESCORE_TARGET_REPORT",
                "target/memory-answer-quality-eval/memory-answer-quality-formal-eval-120-v3-rescored.json"));
        MemoryAnswerQualityFormalEvaluationService service = new MemoryAnswerQualityFormalEvaluationService(
                new MemoryAnswerQualityEvaluationService(),
                new CostTrackingService(),
                new JiTokenCountEstimator(new JiTokenService()),
                new ObjectMapper());

        var report = service.rescore(
                source,
                MemoryAnswerQualityDatasetLoader.load(),
                new MemoryAnswerQualityEvaluationService.RunMetadata(
                        "memory-answer-quality-formal-eval-120-v3-rescored",
                        modelName,
                        judgeModelName,
                        MemoryAnswerQualityFormalEvaluationIT.ANSWER_PROMPT_VERSION,
                        MemoryAnswerQualityFormalEvaluationIT.JUDGE_RUBRIC_VERSION,
                        MemoryAnswerQualityFormalEvaluationIT.SCORER_VERSION,
                        MemoryAnswerQualityDatasetLoader.DATASET_VERSION),
                MemoryAnswerQualityEvaluationService.QualityThresholds.productionDefaults(),
                target);

        assertThat(report.status()).isEqualTo(MemoryAnswerQualityFormalEvaluationService.RunStatus.COMPLETED);
        assertThat(report.evaluatedCases()).isEqualTo(120);
        assertThat(report.resumedCases()).isEqualTo(120);
        assertThat(report.evaluationReport().qualityGatePassed()).isTrue();
        assertThat(Files.exists(target)).isTrue();
    }

    private static String value(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) value = System.getProperty(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
