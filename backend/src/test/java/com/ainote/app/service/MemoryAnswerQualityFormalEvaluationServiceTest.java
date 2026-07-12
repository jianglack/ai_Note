package com.ainote.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryAnswerQualityFormalEvaluationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void disabledRunIsBlockedBeforeAnyModelCallAndWritesReport() {
        AtomicInteger calls = new AtomicInteger();
        var service = service();
        var request = request(false, "blocked-run", 1);

        var report = service.run(
                List.of(answerCase("case-1")),
                (answerCase, attempt) -> {
                    calls.incrementAndGet();
                    return completed(answerCase.id(), attempt);
                },
                request);

        assertThat(report.status()).isEqualTo(MemoryAnswerQualityFormalEvaluationService.RunStatus.BLOCKED);
        assertThat(report.blockReasons()).contains("formal_evaluation_disabled");
        assertThat(calls).hasValue(0);
        assertThat(Files.exists(Path.of(report.reportPath()))).isTrue();
    }

    @Test
    void completedProgressIsResumedWithoutRepeatingModelCalls() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var service = service();
        var request = request(true, "resume-run", 2);
        List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> cases =
                List.of(answerCase("case-1"), answerCase("case-2"));

        var first = service.run(cases, (answerCase, attempt) -> {
            calls.incrementAndGet();
            return completed(answerCase.id(), attempt);
        }, request);
        var second = service.run(cases, (answerCase, attempt) -> {
            calls.incrementAndGet();
            return completed(answerCase.id(), attempt);
        }, request);

        assertThat(first.status()).isEqualTo(MemoryAnswerQualityFormalEvaluationService.RunStatus.COMPLETED);
        assertThat(first.evaluationReport().qualityGatePassed())
                .as("gate failures: %s", first.evaluationReport().gateFailures())
                .isTrue();
        assertThat(second.resumedCases()).isEqualTo(2);
        assertThat(calls).hasValue(2);
        assertThat(Files.readAllLines(Path.of(request.progressPath()))).hasSize(2);
    }

    @Test
    void unavailableCaseIsRetriedAndFinalAttemptIsPersisted() {
        AtomicInteger calls = new AtomicInteger();
        var service = service();
        var request = request(true, "retry-run", 1);

        var report = service.run(List.of(answerCase("case-1")), (answerCase, attempt) -> {
            calls.incrementAndGet();
            if (attempt == 1) {
                return new MemoryAnswerQualityEvaluationService.CaseExecution(
                        answerCase.id(),
                        MemoryAnswerQualityEvaluationService.CaseStatus.UNAVAILABLE,
                        "",
                        "",
                        MemoryAnswerQualityEvaluationService.ModelJudgement.unavailable("temporary"),
                        1,
                        1,
                        attempt,
                        "temporary");
            }
            return completed(answerCase.id(), attempt);
        }, request);

        assertThat(report.status()).isEqualTo(MemoryAnswerQualityFormalEvaluationService.RunStatus.COMPLETED);
        assertThat(report.retriedCases()).isEqualTo(1);
        assertThat(report.results().get(0).attemptCount()).isEqualTo(2);
        assertThat(calls).hasValue(2);
    }

    @Test
    void incompatiblePromptVersionDoesNotReuseCompletedProgress() {
        AtomicInteger calls = new AtomicInteger();
        var service = service();
        var firstRequest = request(true, "fingerprint-run", 1, "prompt-v1");
        var secondRequest = request(true, "fingerprint-run", 1, "prompt-v2");

        service.run(List.of(answerCase("case-1")), (answerCase, attempt) -> {
            calls.incrementAndGet();
            return completed(answerCase.id(), attempt);
        }, firstRequest);
        var second = service.run(List.of(answerCase("case-1")), (answerCase, attempt) -> {
            calls.incrementAndGet();
            return completed(answerCase.id(), attempt);
        }, secondRequest);

        assertThat(second.resumedCases()).isZero();
        assertThat(calls).hasValue(2);
    }

    @Test
    void rescoreReusesPersistedAnswersWithoutCallingModelAgain() {
        AtomicInteger calls = new AtomicInteger();
        var service = service();
        var sourceRequest = request(true, "rescore-source", 1, "prompt-v1");
        List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> cases = List.of(answerCase("case-1"));
        var source = service.run(cases, (answerCase, attempt) -> {
            calls.incrementAndGet();
            return completed(answerCase.id(), attempt);
        }, sourceRequest);
        Path targetPath = tempDir.resolve("rescored.json");
        var targetRun = new MemoryAnswerQualityEvaluationService.RunMetadata(
                "rescore-target",
                "test-model",
                "test-model",
                "prompt-v1",
                "rubric-v1",
                "scorer-v2",
                "dataset-v2");

        var rescored = service.rescore(
                Path.of(source.reportPath()),
                cases,
                targetRun,
                thresholds(1),
                targetPath);

        assertThat(rescored.status()).isEqualTo(MemoryAnswerQualityFormalEvaluationService.RunStatus.COMPLETED);
        assertThat(rescored.resumedCases()).isEqualTo(1);
        assertThat(rescored.evaluationReport().run().scorerVersion()).isEqualTo("scorer-v2");
        assertThat(Files.exists(targetPath)).isTrue();
        assertThat(calls).hasValue(1);
    }

    private MemoryAnswerQualityFormalEvaluationService service() {
        CostTrackingService cost = new CostTrackingService();
        CostTrackingService.ModelPrice price = new CostTrackingService.ModelPrice();
        price.setInput(0.001);
        price.setOutput(0.002);
        cost.setPrices(Map.of("test-model", price));
        return new MemoryAnswerQualityFormalEvaluationService(
                new MemoryAnswerQualityEvaluationService(),
                cost,
                new JiTokenCountEstimator(new JiTokenService()),
                new ObjectMapper());
    }

    private MemoryAnswerQualityFormalEvaluationService.FormalEvaluationRequest request(
            boolean enabled,
            String runId,
            int minCases) {
        return request(enabled, runId, minCases, "prompt-v1");
    }

    private MemoryAnswerQualityFormalEvaluationService.FormalEvaluationRequest request(
            boolean enabled,
            String runId,
            int minCases,
            String promptVersion) {
        Path progress = tempDir.resolve(runId + ".progress.jsonl");
        Path report = tempDir.resolve(runId + ".json");
        return new MemoryAnswerQualityFormalEvaluationService.FormalEvaluationRequest(
                enabled,
                true,
                new MemoryAnswerQualityEvaluationService.RunMetadata(
                        runId,
                        "test-model",
                        "test-model",
                        promptVersion,
                        "rubric-v1",
                        "scorer-v1",
                        "dataset-v1"),
                minCases,
                minCases,
                100_000,
                64,
                64,
                10.0,
                true,
                5_000,
                true,
                true,
                3,
                0,
                1,
                progress.toString(),
                report.toString(),
                thresholds(minCases));
    }

    private MemoryAnswerQualityEvaluationService.QualityThresholds thresholds(int minCases) {
        return new MemoryAnswerQualityEvaluationService.QualityThresholds(
                minCases,
                1.0,
                1.0,
                1.0,
                1.0,
                0.0,
                1.0,
                1.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.8,
                1_000,
                1_000);
    }

    private MemoryAnswerQualityEvaluationService.AnswerQualityCase answerCase(String id) {
        return new MemoryAnswerQualityEvaluationService.AnswerQualityCase(
                id,
                MemoryAnswerQualityEvaluationService.ScenarioType.PREFERENCE_ADHERENCE,
                "请回答",
                "",
                "<user_memory>请用中文</user_memory>",
                List.of(List.of("中文")),
                List.of("English only"),
                "遵循中文偏好",
                new MemoryAnswerQualityEvaluationService.CaseProvenance(
                        "reviewed_adversarial",
                        "test",
                        List.of("preference"),
                        "approved",
                        "reviewer",
                        "2026-07-10T00:00:00Z"));
    }

    private MemoryAnswerQualityEvaluationService.CaseExecution completed(String id, int attempt) {
        return new MemoryAnswerQualityEvaluationService.CaseExecution(
                id,
                MemoryAnswerQualityEvaluationService.CaseStatus.COMPLETED,
                "I do not have a saved preference for this request.",
                "中文",
                new MemoryAnswerQualityEvaluationService.ModelJudgement(
                        true, true, true, true, 0.3, 0.95, "pass"),
                10,
                5,
                attempt,
                "");
    }
}
