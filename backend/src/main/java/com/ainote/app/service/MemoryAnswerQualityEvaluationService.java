package com.ainote.app.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class MemoryAnswerQualityEvaluationService {

    private static final Set<ScenarioType> BENEFIT_SCENARIOS = Set.of(
            ScenarioType.PREFERENCE_ADHERENCE,
            ScenarioType.MEMORY_BENEFIT);

    public EvaluationReport evaluate(RunMetadata run,
                                     List<AnswerQualityCase> cases,
                                     List<CaseExecution> executions,
                                     QualityThresholds thresholds) {
        RunMetadata safeRun = run == null ? RunMetadata.empty() : run;
        List<AnswerQualityCase> safeCases = cases == null ? List.of() : List.copyOf(cases);
        QualityThresholds safeThresholds = thresholds == null
                ? QualityThresholds.productionDefaults()
                : thresholds;
        Map<String, CaseExecution> byCaseId = executions == null
                ? Map.of()
                : executions.stream().collect(Collectors.toMap(
                        CaseExecution::caseId,
                        execution -> execution,
                        (left, right) -> right,
                        LinkedHashMap::new));

        List<ScoredCase> scoredCases = new ArrayList<>();
        for (AnswerQualityCase answerCase : safeCases) {
            CaseExecution execution = byCaseId.getOrDefault(
                    answerCase.id(),
                    CaseExecution.missing(answerCase.id()));
            scoredCases.add(score(answerCase, execution, safeThresholds.minTreatmentJudgeScore()));
        }

        QualityMetrics metrics = metrics(scoredCases);
        List<QualityGateFailure> gateFailures = gateFailures(metrics, safeThresholds);
        return new EvaluationReport(
                safeRun,
                safeThresholds,
                metrics,
                gateFailures.isEmpty(),
                gateFailures,
                scoredCases);
    }

    ScoredCase score(AnswerQualityCase answerCase,
                     CaseExecution execution,
                     double minTreatmentJudgeScore) {
        String treatmentAnswer = normalize(execution.treatmentAnswer());
        String controlAnswer = normalize(execution.controlAnswer());
        boolean completed = execution.status() == CaseStatus.COMPLETED
                && execution.judgement().available();
        boolean requiredEvidencePresent = matchesAllEvidenceGroups(
                treatmentAnswer,
                answerCase.requiredEvidenceGroups());
        List<String> leakedEvidence = answerCase.forbiddenEvidence().stream()
                .filter(value -> containsIgnoreCase(treatmentAnswer, value))
                .toList();
        boolean deterministicContractPassed = requiredEvidencePresent && leakedEvidence.isEmpty();
        boolean judgePassed = completed
                && execution.judgement().casePassed()
                && execution.judgement().policyCompliant()
                && (!BENEFIT_SCENARIOS.contains(answerCase.scenarioType())
                || execution.judgement().treatmentScore() >= minTreatmentJudgeScore);
        boolean treatmentWin = completed
                && (execution.judgement().treatmentPreferred()
                || execution.judgement().treatmentScore() > execution.judgement().controlScore() + 0.05);
        boolean harmfulRegression = completed
                && (!execution.judgement().policyCompliant()
                || !leakedEvidence.isEmpty()
                || execution.judgement().treatmentScore() + 0.05 < execution.judgement().controlScore());
        boolean controlMemoryHonest = !requiresMissingMemoryAcknowledgement(answerCase)
                || acknowledgesMissingMemory(controlAnswer);
        boolean casePassed = completed
                && deterministicContractPassed
                && judgePassed
                && !harmfulRegression
                && (!BENEFIT_SCENARIOS.contains(answerCase.scenarioType()) || treatmentWin);

        return new ScoredCase(
                answerCase.id(),
                answerCase.scenarioType(),
                execution.status(),
                completed,
                controlMemoryHonest,
                deterministicContractPassed,
                judgePassed,
                treatmentWin,
                harmfulRegression,
                casePassed,
                leakedEvidence,
                controlAnswer,
                treatmentAnswer,
                execution.judgement(),
                execution.generationLatencyMillis(),
                execution.judgeLatencyMillis(),
                execution.attemptCount(),
                execution.error(),
                answerCase.provenance());
    }

    private QualityMetrics metrics(List<ScoredCase> cases) {
        int total = cases.size();
        int available = count(cases, ScoredCase::available);
        int benefitCases = count(cases, value -> BENEFIT_SCENARIOS.contains(value.scenarioType()));
        int missingMemoryCases = count(cases, value ->
                value.scenarioType() == ScenarioType.PREFERENCE_ADHERENCE);
        long generationP95 = p95(cases.stream().map(ScoredCase::generationLatencyMillis).toList());
        long judgeP95 = p95(cases.stream().map(ScoredCase::judgeLatencyMillis).toList());
        return new QualityMetrics(
                total,
                available,
                rate(available, total),
                rate(count(cases, ScoredCase::deterministicContractPassed), total),
                rate(count(cases, ScoredCase::judgePassed), total),
                rate(count(cases, value -> BENEFIT_SCENARIOS.contains(value.scenarioType()) && value.treatmentWin()),
                        benefitCases),
                rate(count(cases, ScoredCase::harmfulRegression), total),
                rate(count(cases, value -> value.scenarioType() == ScenarioType.PREFERENCE_ADHERENCE
                                && value.controlMemoryHonest()),
                        missingMemoryCases),
                scenarioPassRate(cases, ScenarioType.PREFERENCE_ADHERENCE),
                scenarioPassRate(cases, ScenarioType.MEMORY_BENEFIT),
                scenarioPassRate(cases, ScenarioType.STALE_MEMORY_ISOLATION),
                scenarioPassRate(cases, ScenarioType.SELECTED_NOTE_PRIORITY),
                scenarioPassRate(cases, ScenarioType.RAG_MEMORY_CONFLICT),
                scenarioPassRate(cases, ScenarioType.DELETED_DISABLED_ISOLATION),
                generationP95,
                judgeP95,
                count(cases, value -> value.status() == CaseStatus.TIMEOUT),
                count(cases, value -> value.status() == CaseStatus.ERROR));
    }

    private List<QualityGateFailure> gateFailures(QualityMetrics metrics, QualityThresholds thresholds) {
        List<QualityGateFailure> failures = new ArrayList<>();
        requireAtLeast(failures, "minimum_cases", "totalCases", metrics.totalCases(), thresholds.minTotalCases());
        requireAtLeast(failures, "availability", "availabilityRate", metrics.availabilityRate(), thresholds.minAvailabilityRate());
        requireAtLeast(failures, "deterministic_contract", "deterministicContractPassRate",
                metrics.deterministicContractPassRate(), thresholds.minDeterministicContractPassRate());
        requireAtLeast(failures, "judge_quality", "judgePassRate", metrics.judgePassRate(), thresholds.minJudgePassRate());
        requireAtLeast(failures, "treatment_benefit", "treatmentWinRate", metrics.treatmentWinRate(), thresholds.minTreatmentWinRate());
        requireAtMost(failures, "harmful_regression", "harmfulRegressionRate", metrics.harmfulRegressionRate(), thresholds.maxHarmfulRegressionRate());
        requireAtLeast(failures, "missing_memory_honesty", "missingMemoryHonestyRate",
                metrics.missingMemoryHonestyRate(), thresholds.minMissingMemoryHonestyRate());
        requireAtLeast(failures, "preference_adherence", "preferenceAdherenceRate", metrics.preferenceAdherenceRate(), thresholds.minPreferenceAdherenceRate());
        requireAtLeast(failures, "memory_benefit", "memoryBenefitRate", metrics.memoryBenefitRate(), thresholds.minMemoryBenefitRate());
        requireAtLeast(failures, "stale_memory_isolation", "staleMemoryIsolationRate", metrics.staleMemoryIsolationRate(), thresholds.minStaleMemoryIsolationRate());
        requireAtLeast(failures, "selected_note_priority", "selectedNotePriorityRate", metrics.selectedNotePriorityRate(), thresholds.minSelectedNotePriorityRate());
        requireAtLeast(failures, "rag_memory_conflict", "ragMemoryConflictResolutionRate", metrics.ragMemoryConflictResolutionRate(), thresholds.minRagMemoryConflictResolutionRate());
        requireAtLeast(failures, "deleted_disabled_isolation", "deletedDisabledIsolationRate", metrics.deletedDisabledIsolationRate(), thresholds.minDeletedDisabledIsolationRate());
        requireAtMost(failures, "generation_latency", "generationP95Millis", metrics.generationP95Millis(), thresholds.maxGenerationP95Millis());
        requireAtMost(failures, "judge_latency", "judgeP95Millis", metrics.judgeP95Millis(), thresholds.maxJudgeP95Millis());
        requireAtMost(failures, "timeouts", "timeoutCases", metrics.timeoutCases(), 0);
        requireAtMost(failures, "errors", "errorCases", metrics.errorCases(), 0);
        return List.copyOf(failures);
    }

    private double scenarioPassRate(List<ScoredCase> cases, ScenarioType type) {
        List<ScoredCase> selected = cases.stream().filter(value -> value.scenarioType() == type).toList();
        return rate(count(selected, ScoredCase::casePassed), selected.size());
    }

    private boolean matchesAllEvidenceGroups(String answer, List<List<String>> groups) {
        if (groups == null || groups.isEmpty()) {
            return true;
        }
        return groups.stream().allMatch(group -> group != null
                && !group.isEmpty()
                && group.stream().anyMatch(value -> containsIgnoreCase(answer, value)));
    }

    private boolean requiresMissingMemoryAcknowledgement(AnswerQualityCase answerCase) {
        return answerCase.scenarioType() == ScenarioType.PREFERENCE_ADHERENCE
                && !answerCase.controlContext().contains("<user_memory>");
    }

    private boolean acknowledgesMissingMemory(String answer) {
        String normalized = normalize(answer).toLowerCase(Locale.ROOT);
        return containsAny(normalized,
                "没有可用", "没有对应", "未找到", "无法根据", "无法访问", "不能确定", "不知道",
                "不能假装知道", "没有与你这个问题对应的可用长期记忆",
                "no applicable memory", "no saved preference", "no active saved preference",
                "do not have a saved preference", "don't have a saved preference",
                "do not have an applicable active saved preference",
                "cannot access a saved preference", "unable to find a saved preference",
                "cannot pretend to know");
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) return true;
        }
        return false;
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        return normalize(haystack).toLowerCase(Locale.ROOT)
                .contains(normalize(needle).toLowerCase(Locale.ROOT));
    }

    private int count(List<ScoredCase> values, Predicate<ScoredCase> predicate) {
        return (int) values.stream().filter(predicate).count();
    }

    private double rate(long numerator, long denominator) {
        return denominator <= 0 ? 0.0 : (double) numerator / denominator;
    }

    private long p95(List<Long> values) {
        List<Long> sorted = values.stream()
                .filter(value -> value != null && value >= 0)
                .sorted(Comparator.naturalOrder())
                .toList();
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95) - 1);
        return sorted.get(index);
    }

    private void requireAtLeast(List<QualityGateFailure> failures,
                                String gate,
                                String metric,
                                double actual,
                                double expected) {
        if (actual < expected) {
            failures.add(new QualityGateFailure(gate, metric, actual, expected, "metric is below minimum"));
        }
    }

    private void requireAtMost(List<QualityGateFailure> failures,
                               String gate,
                               String metric,
                               double actual,
                               double expected) {
        if (actual > expected) {
            failures.add(new QualityGateFailure(gate, metric, actual, expected, "metric is above maximum"));
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public enum ScenarioType {
        PREFERENCE_ADHERENCE,
        MEMORY_BENEFIT,
        STALE_MEMORY_ISOLATION,
        SELECTED_NOTE_PRIORITY,
        RAG_MEMORY_CONFLICT,
        DELETED_DISABLED_ISOLATION
    }

    public enum CaseStatus {
        COMPLETED,
        UNAVAILABLE,
        TIMEOUT,
        ERROR
    }

    public record CaseProvenance(String sourceType,
                                 String sourceReference,
                                 List<String> scenarioTags,
                                 String reviewStatus,
                                 String reviewer,
                                 String approvedAt) {
        public CaseProvenance {
            sourceType = normalize(sourceType);
            sourceReference = normalize(sourceReference);
            scenarioTags = scenarioTags == null ? List.of() : List.copyOf(scenarioTags);
            reviewStatus = normalize(reviewStatus);
            reviewer = normalize(reviewer);
            approvedAt = normalize(approvedAt);
        }
    }

    public record AnswerQualityCase(String id,
                                    ScenarioType scenarioType,
                                    String query,
                                    String controlContext,
                                    String treatmentContext,
                                    List<List<String>> requiredEvidenceGroups,
                                    List<String> forbiddenEvidence,
                                    String judgeRubric,
                                    CaseProvenance provenance) {
        public AnswerQualityCase {
            id = normalize(id);
            scenarioType = scenarioType == null ? ScenarioType.MEMORY_BENEFIT : scenarioType;
            query = normalize(query);
            controlContext = normalize(controlContext);
            treatmentContext = normalize(treatmentContext);
            requiredEvidenceGroups = requiredEvidenceGroups == null
                    ? List.of()
                    : requiredEvidenceGroups.stream()
                    .map(group -> group == null ? List.<String>of() : List.copyOf(group))
                    .toList();
            forbiddenEvidence = forbiddenEvidence == null ? List.of() : List.copyOf(forbiddenEvidence);
            judgeRubric = normalize(judgeRubric);
            provenance = provenance == null
                    ? new CaseProvenance("", "", List.of(), "", "", "")
                    : provenance;
        }
    }

    public record ModelJudgement(boolean available,
                                 boolean casePassed,
                                 boolean policyCompliant,
                                 boolean treatmentPreferred,
                                 double controlScore,
                                 double treatmentScore,
                                 String reason) {
        public ModelJudgement {
            controlScore = clamp(controlScore);
            treatmentScore = clamp(treatmentScore);
            reason = normalize(reason);
        }

        public static ModelJudgement unavailable(String reason) {
            return new ModelJudgement(false, false, false, false, 0.0, 0.0, reason);
        }

        private static double clamp(double value) {
            return Math.max(0.0, Math.min(1.0, value));
        }
    }

    public record CaseExecution(String caseId,
                                CaseStatus status,
                                String controlAnswer,
                                String treatmentAnswer,
                                ModelJudgement judgement,
                                long generationLatencyMillis,
                                long judgeLatencyMillis,
                                int attemptCount,
                                String error) {
        public CaseExecution {
            caseId = normalize(caseId);
            status = status == null ? CaseStatus.ERROR : status;
            controlAnswer = normalize(controlAnswer);
            treatmentAnswer = normalize(treatmentAnswer);
            judgement = judgement == null ? ModelJudgement.unavailable("missing judgement") : judgement;
            generationLatencyMillis = Math.max(0, generationLatencyMillis);
            judgeLatencyMillis = Math.max(0, judgeLatencyMillis);
            attemptCount = Math.max(1, attemptCount);
            error = normalize(error);
        }

        public static CaseExecution missing(String caseId) {
            return new CaseExecution(
                    caseId,
                    CaseStatus.ERROR,
                    "",
                    "",
                    ModelJudgement.unavailable("missing execution"),
                    0,
                    0,
                    1,
                    "missing execution");
        }
    }

    public record ScoredCase(String caseId,
                             ScenarioType scenarioType,
                             CaseStatus status,
                             boolean available,
                             boolean controlMemoryHonest,
                             boolean deterministicContractPassed,
                             boolean judgePassed,
                             boolean treatmentWin,
                             boolean harmfulRegression,
                             boolean casePassed,
                             List<String> leakedEvidence,
                             String controlAnswer,
                             String treatmentAnswer,
                             ModelJudgement judgement,
                             long generationLatencyMillis,
                             long judgeLatencyMillis,
                             int attemptCount,
                             String error,
                             CaseProvenance provenance) {
        public ScoredCase {
            leakedEvidence = leakedEvidence == null ? List.of() : List.copyOf(leakedEvidence);
        }
    }

    public record RunMetadata(String runId,
                              String modelName,
                              String judgeModelName,
                              String promptVersion,
                              String rubricVersion,
                              String scorerVersion,
                              String datasetVersion) {
        public RunMetadata {
            runId = normalize(runId);
            modelName = normalize(modelName);
            judgeModelName = normalize(judgeModelName);
            promptVersion = normalize(promptVersion);
            rubricVersion = normalize(rubricVersion);
            scorerVersion = normalize(scorerVersion);
            datasetVersion = normalize(datasetVersion);
        }

        static RunMetadata empty() {
            return new RunMetadata("", "", "", "", "", "", "");
        }
    }

    public record QualityThresholds(int minTotalCases,
                                    double minAvailabilityRate,
                                    double minDeterministicContractPassRate,
                                    double minJudgePassRate,
                                    double minTreatmentWinRate,
                                    double maxHarmfulRegressionRate,
                                    double minMissingMemoryHonestyRate,
                                    double minPreferenceAdherenceRate,
                                    double minMemoryBenefitRate,
                                    double minStaleMemoryIsolationRate,
                                    double minSelectedNotePriorityRate,
                                    double minRagMemoryConflictResolutionRate,
                                    double minDeletedDisabledIsolationRate,
                                    double minTreatmentJudgeScore,
                                    long maxGenerationP95Millis,
                                    long maxJudgeP95Millis) {
        public static QualityThresholds productionDefaults() {
            return new QualityThresholds(
                    120,
                    0.995,
                    0.95,
                    0.95,
                    0.80,
                    0.01,
                    1.0,
                    0.95,
                    0.90,
                    1.0,
                    1.0,
                    1.0,
                    1.0,
                    0.80,
                    15_000,
                    15_000);
        }
    }

    public record QualityMetrics(int totalCases,
                                 int availableCases,
                                 double availabilityRate,
                                 double deterministicContractPassRate,
                                 double judgePassRate,
                                 double treatmentWinRate,
                                 double harmfulRegressionRate,
                                 double missingMemoryHonestyRate,
                                 double preferenceAdherenceRate,
                                 double memoryBenefitRate,
                                 double staleMemoryIsolationRate,
                                 double selectedNotePriorityRate,
                                 double ragMemoryConflictResolutionRate,
                                 double deletedDisabledIsolationRate,
                                 long generationP95Millis,
                                 long judgeP95Millis,
                                 int timeoutCases,
                                 int errorCases) {
    }

    public record QualityGateFailure(String gate,
                                     String metric,
                                     double actual,
                                     double expected,
                                     String message) {
    }

    public record EvaluationReport(RunMetadata run,
                                   QualityThresholds thresholds,
                                   QualityMetrics metrics,
                                   boolean qualityGatePassed,
                                   List<QualityGateFailure> gateFailures,
                                   List<ScoredCase> cases) {
        public EvaluationReport {
            gateFailures = gateFailures == null ? List.of() : List.copyOf(gateFailures);
            cases = cases == null ? List.of() : List.copyOf(cases);
        }
    }
}
