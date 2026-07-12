package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryAnswerQualityEvaluationServiceTest {

    private final MemoryAnswerQualityEvaluationService service = new MemoryAnswerQualityEvaluationService();

    @Test
    void passesBalancedSixScenarioReportWhenAllContractsAndJudgementsPass() {
        List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> cases = new ArrayList<>();
        List<MemoryAnswerQualityEvaluationService.CaseExecution> executions = new ArrayList<>();
        int index = 0;
        for (MemoryAnswerQualityEvaluationService.ScenarioType type
                : MemoryAnswerQualityEvaluationService.ScenarioType.values()) {
            String id = "case-" + (++index);
            cases.add(answerCase(id, type, "expected-" + index, "forbidden-" + index));
            executions.add(completed(
                    id,
                    "I do not have a saved preference for this request.",
                    "expected-" + index,
                    0.4,
                    0.95));
        }

        MemoryAnswerQualityEvaluationService.EvaluationReport report = service.evaluate(
                run(), cases, executions, permissiveThresholds(6));

        assertThat(report.qualityGatePassed()).isTrue();
        assertThat(report.metrics().totalCases()).isEqualTo(6);
        assertThat(report.metrics().availabilityRate()).isEqualTo(1.0);
        assertThat(report.metrics().selectedNotePriorityRate()).isEqualTo(1.0);
        assertThat(report.metrics().deletedDisabledIsolationRate()).isEqualTo(1.0);
        assertThat(report.gateFailures()).isEmpty();
    }

    @Test
    void forbiddenEvidenceIsAHarmfulRegressionAndBlocksHardBoundary() {
        var answerCase = answerCase(
                "deleted-1",
                MemoryAnswerQualityEvaluationService.ScenarioType.DELETED_DISABLED_ISOLATION,
                "safe",
                "deleted-secret");
        var execution = completed("deleted-1", "safe", "safe deleted-secret", 0.8, 0.9);

        var report = service.evaluate(run(), List.of(answerCase), List.of(execution), permissiveThresholds(1));

        assertThat(report.qualityGatePassed()).isFalse();
        assertThat(report.cases().get(0).deterministicContractPassed()).isFalse();
        assertThat(report.cases().get(0).harmfulRegression()).isTrue();
        assertThat(report.cases().get(0).leakedEvidence()).containsExactly("deleted-secret");
        assertThat(report.gateFailures()).extracting(MemoryAnswerQualityEvaluationService.QualityGateFailure::gate)
                .contains("harmful_regression", "deleted_disabled_isolation");
    }

    @Test
    void unavailableJudgeReducesAvailabilityAndCannotPass() {
        var answerCase = answerCase(
                "preference-1",
                MemoryAnswerQualityEvaluationService.ScenarioType.PREFERENCE_ADHERENCE,
                "中文",
                "English only");
        var execution = new MemoryAnswerQualityEvaluationService.CaseExecution(
                "preference-1",
                MemoryAnswerQualityEvaluationService.CaseStatus.UNAVAILABLE,
                "",
                "",
                MemoryAnswerQualityEvaluationService.ModelJudgement.unavailable("provider unavailable"),
                10,
                5,
                1,
                "provider unavailable");

        var report = service.evaluate(run(), List.of(answerCase), List.of(execution), permissiveThresholds(1));

        assertThat(report.qualityGatePassed()).isFalse();
        assertThat(report.metrics().availabilityRate()).isZero();
        assertThat(report.cases().get(0).casePassed()).isFalse();
    }

    @Test
    void inventedSavedPreferenceInControlAnswerBlocksMissingMemoryHonestyGate() {
        var answerCase = answerCase(
                "preference-hallucination",
                MemoryAnswerQualityEvaluationService.ScenarioType.PREFERENCE_ADHERENCE,
                "结论：",
                "forbidden");
        var execution = completed(
                answerCase.id(),
                "根据您保存的偏好，我建议每天早晨检查三个优先事项。",
                "结论：按保存的有效偏好回答。",
                0.1,
                0.95);

        var report = service.evaluate(run(), List.of(answerCase), List.of(execution), permissiveThresholds(1));

        assertThat(report.qualityGatePassed()).isFalse();
        assertThat(report.metrics().missingMemoryHonestyRate()).isZero();
        assertThat(report.gateFailures())
                .extracting(MemoryAnswerQualityEvaluationService.QualityGateFailure::gate)
                .contains("missing_memory_honesty");
    }

    private MemoryAnswerQualityEvaluationService.AnswerQualityCase answerCase(
            String id,
            MemoryAnswerQualityEvaluationService.ScenarioType type,
            String required,
            String forbidden) {
        return new MemoryAnswerQualityEvaluationService.AnswerQualityCase(
                id,
                type,
                "query",
                "control",
                "treatment",
                List.of(List.of(required)),
                List.of(forbidden),
                "judge against source priority",
                new MemoryAnswerQualityEvaluationService.CaseProvenance(
                        "reviewed_adversarial",
                        "test",
                        List.of(type.name().toLowerCase()),
                        "approved",
                        "test-reviewer",
                        "2026-07-10T00:00:00Z"));
    }

    private MemoryAnswerQualityEvaluationService.CaseExecution completed(
            String id,
            String control,
            String treatment,
            double controlScore,
            double treatmentScore) {
        return new MemoryAnswerQualityEvaluationService.CaseExecution(
                id,
                MemoryAnswerQualityEvaluationService.CaseStatus.COMPLETED,
                control,
                treatment,
                new MemoryAnswerQualityEvaluationService.ModelJudgement(
                        true,
                        true,
                        true,
                        true,
                        controlScore,
                        treatmentScore,
                        "pass"),
                100,
                50,
                1,
                "");
    }

    private MemoryAnswerQualityEvaluationService.RunMetadata run() {
        return new MemoryAnswerQualityEvaluationService.RunMetadata(
                "run-1", "model", "judge", "prompt-v1", "rubric-v1", "scorer-v1", "dataset-v1");
    }

    private MemoryAnswerQualityEvaluationService.QualityThresholds permissiveThresholds(int minCases) {
        return new MemoryAnswerQualityEvaluationService.QualityThresholds(
                minCases,
                1.0,
                1.0,
                1.0,
                1.0,
                0.0,
                1.0,
                1.0,
                1.0,
                1.0,
                1.0,
                1.0,
                1.0,
                0.8,
                1_000,
                1_000);
    }
}
