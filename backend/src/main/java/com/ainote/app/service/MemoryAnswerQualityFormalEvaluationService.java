package com.ainote.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MemoryAnswerQualityFormalEvaluationService {

    private static final int PROMPT_OVERHEAD_TOKENS_PER_CASE = 900;
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final MemoryAnswerQualityEvaluationService evaluationService;
    private final CostTrackingService costTrackingService;
    private final JiTokenCountEstimator tokenCountEstimator;
    private final ObjectMapper objectMapper;

    public MemoryAnswerQualityFormalEvaluationService(
            MemoryAnswerQualityEvaluationService evaluationService,
            CostTrackingService costTrackingService,
            JiTokenCountEstimator tokenCountEstimator,
            ObjectMapper objectMapper) {
        this.evaluationService = evaluationService;
        this.costTrackingService = costTrackingService;
        this.tokenCountEstimator = tokenCountEstimator;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public FormalEvaluationReport preflight(
            List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> cases,
            FormalEvaluationRequest request) {
        return preflight(cases, request, true);
    }

    public FormalEvaluationReport run(
            List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> cases,
            CaseRunner runner,
            FormalEvaluationRequest request) {
        FormalEvaluationRequest safeRequest = request == null ? FormalEvaluationRequest.defaults() : request;
        FormalEvaluationReport preflight = preflight(cases, safeRequest, false);
        Path reportPath = Path.of(safeRequest.reportPath());
        if (preflight.status() == RunStatus.BLOCKED) {
            writeReport(reportPath, preflight);
            return preflight;
        }

        List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> selected =
                selectCases(cases, safeRequest.maxCases());
        String runFingerprint = runFingerprint(safeRequest.runMetadata());
        Map<String, MemoryAnswerQualityEvaluationService.CaseExecution> progress =
                safeRequest.resume()
                        ? readProgress(Path.of(safeRequest.progressPath()), runFingerprint)
                        : new LinkedHashMap<>();
        List<MemoryAnswerQualityEvaluationService.CaseExecution> results = new ArrayList<>();
        int resumedCases = 0;
        int retriedCases = 0;
        Instant startedAt = Instant.now();

        ExecutorService executor = Executors.newCachedThreadPool(daemonThreadFactory());
        try {
            for (MemoryAnswerQualityEvaluationService.AnswerQualityCase answerCase : selected) {
                MemoryAnswerQualityEvaluationService.CaseExecution existing = progress.get(answerCase.id());
                if (existing != null && shouldReuse(existing, safeRequest)) {
                    results.add(existing);
                    resumedCases++;
                    continue;
                }

                MemoryAnswerQualityEvaluationService.CaseExecution execution = null;
                int maxAttempts = Math.max(1, safeRequest.maxAttempts());
                for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                    if (attempt > 1) {
                        retriedCases++;
                        sleepBeforeRetry(attempt, safeRequest);
                    }
                    execution = runWithTimeout(executor, runner, answerCase, attempt, safeRequest.perCaseTimeoutMillis());
                    if (execution.status() == MemoryAnswerQualityEvaluationService.CaseStatus.COMPLETED) {
                        break;
                    }
                    if (!safeRequest.retryUnavailable()) {
                        break;
                    }
                }
                results.add(execution);
                appendProgress(Path.of(safeRequest.progressPath()), runFingerprint, execution);
            }
        } finally {
            executor.shutdownNow();
        }

        MemoryAnswerQualityEvaluationService.EvaluationReport evaluationReport = evaluationService.evaluate(
                safeRequest.runMetadata(),
                selected,
                results,
                safeRequest.thresholds());
        FormalEvaluationReport report = new FormalEvaluationReport(
                RunStatus.COMPLETED,
                safeRequest.runMetadata(),
                preflight.budget(),
                List.of(),
                selected.size(),
                results.size(),
                resumedCases,
                retriedCases,
                (int) results.stream().filter(value -> value.status()
                        == MemoryAnswerQualityEvaluationService.CaseStatus.TIMEOUT).count(),
                safeRequest.progressPath(),
                safeRequest.reportPath(),
                evaluationReport,
                results,
                startedAt.toString(),
                Instant.now().toString());
        writeReport(reportPath, report);
        return report;
    }

    public FormalEvaluationReport rescore(
            Path sourceReportPath,
            List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> cases,
            MemoryAnswerQualityEvaluationService.RunMetadata targetRun,
            MemoryAnswerQualityEvaluationService.QualityThresholds thresholds,
            Path targetReportPath) {
        FormalEvaluationReport source = readReport(sourceReportPath);
        if (source.status() != RunStatus.COMPLETED || source.evaluationReport() == null) {
            throw new IllegalArgumentException("source answer-quality report is not completed");
        }
        MemoryAnswerQualityEvaluationService.RunMetadata safeTarget = targetRun == null
                ? MemoryAnswerQualityEvaluationService.RunMetadata.empty()
                : targetRun;
        requireSameModelEvidence(source.run(), safeTarget);
        List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> selected = cases == null
                ? List.of()
                : List.copyOf(cases);
        validateResultCoverage(selected, source.results());

        Instant startedAt = Instant.now();
        MemoryAnswerQualityEvaluationService.EvaluationReport evaluationReport = evaluationService.evaluate(
                safeTarget,
                selected,
                source.results(),
                thresholds);
        FormalEvaluationReport rescored = new FormalEvaluationReport(
                RunStatus.COMPLETED,
                safeTarget,
                source.budget(),
                List.of(),
                selected.size(),
                source.results().size(),
                source.results().size(),
                0,
                (int) source.results().stream().filter(value -> value.status()
                        == MemoryAnswerQualityEvaluationService.CaseStatus.TIMEOUT).count(),
                source.progressPath(),
                targetReportPath.toString(),
                evaluationReport,
                source.results(),
                startedAt.toString(),
                Instant.now().toString());
        writeReport(targetReportPath, rescored);
        return rescored;
    }

    private FormalEvaluationReport preflight(
            List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> cases,
            FormalEvaluationRequest request,
            boolean write) {
        FormalEvaluationRequest safeRequest = request == null ? FormalEvaluationRequest.defaults() : request;
        List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> selected =
                selectCases(cases, safeRequest.maxCases());
        EvaluationBudget budget = estimateBudget(selected, safeRequest);
        List<String> blockReasons = blockReasons(selected, safeRequest, budget);
        FormalEvaluationReport report = new FormalEvaluationReport(
                blockReasons.isEmpty() ? RunStatus.READY : RunStatus.BLOCKED,
                safeRequest.runMetadata(),
                budget,
                blockReasons,
                selected.size(),
                0,
                0,
                0,
                0,
                safeRequest.progressPath(),
                safeRequest.reportPath(),
                null,
                List.of(),
                "",
                "");
        if (write) {
            writeReport(Path.of(safeRequest.reportPath()), report);
        }
        return report;
    }

    private EvaluationBudget estimateBudget(
            List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> cases,
            FormalEvaluationRequest request) {
        int estimatedInputTokens = cases.stream().mapToInt(this::estimateInputTokens).sum();
        int estimatedGenerationOutputTokens = cases.size() * request.estimatedOutputTokensPerCall() * 2;
        int estimatedJudgeOutputTokens = cases.size() * request.estimatedJudgeOutputTokensPerCall();
        double estimatedCost = 0.0;
        if (costTrackingService != null) {
            int generationInput = estimatedInputTokens * 2 / 3;
            int judgeInput = estimatedInputTokens - generationInput;
            estimatedCost += costTrackingService.calculateCost(
                    request.runMetadata().modelName(), generationInput, estimatedGenerationOutputTokens);
            estimatedCost += costTrackingService.calculateCost(
                    request.runMetadata().judgeModelName(), judgeInput, estimatedJudgeOutputTokens);
        }
        return new EvaluationBudget(
                cases.size(),
                estimatedInputTokens,
                estimatedGenerationOutputTokens + estimatedJudgeOutputTokens,
                estimatedCost,
                request.maxInputTokens(),
                request.maxEstimatedCostYuan());
    }

    private int estimateInputTokens(MemoryAnswerQualityEvaluationService.AnswerQualityCase answerCase) {
        String text = answerCase.query()
                + '\n' + answerCase.controlContext()
                + '\n' + answerCase.treatmentContext()
                + '\n' + answerCase.judgeRubric()
                + '\n' + answerCase.requiredEvidenceGroups()
                + '\n' + answerCase.forbiddenEvidence();
        int contentTokens = tokenCountEstimator == null
                ? Math.max(1, text.length() / 3)
                : tokenCountEstimator.estimateTokenCountInText(text);
        return Math.max(1, contentTokens * 3 + PROMPT_OVERHEAD_TOKENS_PER_CASE);
    }

    private List<String> blockReasons(
            List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> cases,
            FormalEvaluationRequest request,
            EvaluationBudget budget) {
        List<String> reasons = new ArrayList<>();
        if (!request.enabled()) reasons.add("formal_evaluation_disabled");
        if (!request.apiKeyConfigured()) reasons.add("api_key_missing");
        if (!hasText(request.runMetadata().modelName())) reasons.add("model_name_missing");
        if (!hasText(request.runMetadata().judgeModelName())) reasons.add("judge_model_name_missing");
        if (!hasText(request.runMetadata().promptVersion())) reasons.add("prompt_version_missing");
        if (!hasText(request.runMetadata().rubricVersion())) reasons.add("rubric_version_missing");
        if (!hasText(request.runMetadata().datasetVersion())) reasons.add("dataset_version_missing");
        if (cases.size() < request.minCases()) reasons.add("insufficient_cases");
        if (budget.estimatedInputTokens() > request.maxInputTokens()) reasons.add("estimated_input_tokens_exceed_budget");
        if (budget.estimatedCostYuan() > request.maxEstimatedCostYuan()) reasons.add("estimated_cost_exceeds_budget");
        if (request.requireKnownPrice() && !hasKnownPrice(request.runMetadata().modelName())) reasons.add("model_price_unknown");
        if (request.requireKnownPrice() && !hasKnownPrice(request.runMetadata().judgeModelName())) reasons.add("judge_model_price_unknown");
        if (request.perCaseTimeoutMillis() <= 0) reasons.add("invalid_per_case_timeout");
        return List.copyOf(reasons);
    }

    private boolean hasKnownPrice(String modelName) {
        if (costTrackingService == null || modelName == null) {
            return false;
        }
        return costTrackingService.getPrices().keySet().stream()
                .anyMatch(key -> modelName.contains(key) || key.contains(modelName));
    }

    private MemoryAnswerQualityEvaluationService.CaseExecution runWithTimeout(
            ExecutorService executor,
            CaseRunner runner,
            MemoryAnswerQualityEvaluationService.AnswerQualityCase answerCase,
            int attempt,
            long timeoutMillis) {
        Future<MemoryAnswerQualityEvaluationService.CaseExecution> future = executor.submit(
                () -> runner.run(answerCase, attempt));
        try {
            MemoryAnswerQualityEvaluationService.CaseExecution result = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            if (result == null) {
                return failure(answerCase.id(), MemoryAnswerQualityEvaluationService.CaseStatus.ERROR,
                        attempt, "runner returned null");
            }
            return new MemoryAnswerQualityEvaluationService.CaseExecution(
                    answerCase.id(),
                    result.status(),
                    result.controlAnswer(),
                    result.treatmentAnswer(),
                    result.judgement(),
                    result.generationLatencyMillis(),
                    result.judgeLatencyMillis(),
                    attempt,
                    result.error());
        } catch (TimeoutException e) {
            future.cancel(true);
            return failure(answerCase.id(), MemoryAnswerQualityEvaluationService.CaseStatus.TIMEOUT,
                    attempt, "case timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return failure(answerCase.id(), MemoryAnswerQualityEvaluationService.CaseStatus.ERROR,
                    attempt, "case interrupted");
        } catch (ExecutionException e) {
            return failure(answerCase.id(), MemoryAnswerQualityEvaluationService.CaseStatus.ERROR,
                    attempt, rootMessage(e));
        }
    }

    private MemoryAnswerQualityEvaluationService.CaseExecution failure(
            String caseId,
            MemoryAnswerQualityEvaluationService.CaseStatus status,
            int attempt,
            String error) {
        return new MemoryAnswerQualityEvaluationService.CaseExecution(
                caseId,
                status,
                "",
                "",
                MemoryAnswerQualityEvaluationService.ModelJudgement.unavailable(error),
                0,
                0,
                attempt,
                error);
    }

    private boolean shouldReuse(MemoryAnswerQualityEvaluationService.CaseExecution execution,
                                FormalEvaluationRequest request) {
        return execution.status() == MemoryAnswerQualityEvaluationService.CaseStatus.COMPLETED
                || !request.retryUnavailable();
    }

    private Map<String, MemoryAnswerQualityEvaluationService.CaseExecution> readProgress(
            Path path,
            String expectedFingerprint) {
        Map<String, MemoryAnswerQualityEvaluationService.CaseExecution> progress = new LinkedHashMap<>();
        if (!Files.exists(path)) {
            return progress;
        }
        try {
            for (String line : Files.readAllLines(path)) {
                if (!line.isBlank()) {
                    ProgressEnvelope envelope = objectMapper.readValue(line, ProgressEnvelope.class);
                    if (expectedFingerprint.equals(envelope.runFingerprint()) && envelope.execution() != null) {
                        progress.put(envelope.execution().caseId(), envelope.execution());
                    }
                }
            }
            return progress;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read answer-quality progress: " + path, e);
        }
    }

    private void appendProgress(
            Path path,
            String runFingerprint,
            MemoryAnswerQualityEvaluationService.CaseExecution execution) {
        try {
            createParentDirectories(path);
            Files.writeString(
                    path,
                    objectMapper.writeValueAsString(new ProgressEnvelope(runFingerprint, execution))
                            + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to append answer-quality progress: " + path, e);
        }
    }

    private void writeReport(Path path, FormalEvaluationReport report) {
        try {
            createParentDirectories(path);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), report);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write answer-quality report: " + path, e);
        }
    }

    private FormalEvaluationReport readReport(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), FormalEvaluationReport.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read answer-quality report: " + path, e);
        }
    }

    private void requireSameModelEvidence(
            MemoryAnswerQualityEvaluationService.RunMetadata source,
            MemoryAnswerQualityEvaluationService.RunMetadata target) {
        if (source == null
                || !source.modelName().equals(target.modelName())
                || !source.judgeModelName().equals(target.judgeModelName())
                || !source.promptVersion().equals(target.promptVersion())
                || !source.rubricVersion().equals(target.rubricVersion())) {
            throw new IllegalArgumentException("source report model or prompt evidence is incompatible");
        }
    }

    private void validateResultCoverage(
            List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> cases,
            List<MemoryAnswerQualityEvaluationService.CaseExecution> results) {
        java.util.Set<String> caseIds = cases.stream()
                .map(MemoryAnswerQualityEvaluationService.AnswerQualityCase::id)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> resultIds = results.stream()
                .map(MemoryAnswerQualityEvaluationService.CaseExecution::caseId)
                .collect(java.util.stream.Collectors.toSet());
        if (!caseIds.equals(resultIds)) {
            throw new IllegalArgumentException("source report result coverage does not match target dataset");
        }
    }

    private void createParentDirectories(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> selectCases(
            List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> cases,
            int maxCases) {
        List<MemoryAnswerQualityEvaluationService.AnswerQualityCase> safe = cases == null
                ? List.of()
                : List.copyOf(cases);
        if (maxCases <= 0 || safe.size() <= maxCases) {
            return safe;
        }
        return List.copyOf(safe.subList(0, maxCases));
    }

    private void sleepBeforeRetry(int attempt, FormalEvaluationRequest request) {
        long multiplier = 1L << Math.min(20, Math.max(0, attempt - 2));
        long delay = Math.min(request.retryMaxBackoffMillis(), request.retryInitialBackoffMillis() * multiplier);
        if (delay <= 0) return;
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String runFingerprint(MemoryAnswerQualityEvaluationService.RunMetadata run) {
        String value = String.join("|",
                run.runId(),
                run.modelName(),
                run.judgeModelName(),
                run.promptVersion(),
                run.rubricVersion(),
                run.scorerVersion(),
                run.datasetVersion());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) hex.append(String.format("%02x", item));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static ThreadFactory daemonThreadFactory() {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "memory-answer-quality-eval-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @FunctionalInterface
    public interface CaseRunner {
        MemoryAnswerQualityEvaluationService.CaseExecution run(
                MemoryAnswerQualityEvaluationService.AnswerQualityCase answerCase,
                int attempt) throws Exception;
    }

    public enum RunStatus {
        READY,
        BLOCKED,
        COMPLETED
    }

    public record FormalEvaluationRequest(
            boolean enabled,
            boolean apiKeyConfigured,
            MemoryAnswerQualityEvaluationService.RunMetadata runMetadata,
            int maxCases,
            int minCases,
            int maxInputTokens,
            int estimatedOutputTokensPerCall,
            int estimatedJudgeOutputTokensPerCall,
            double maxEstimatedCostYuan,
            boolean requireKnownPrice,
            long perCaseTimeoutMillis,
            boolean resume,
            boolean retryUnavailable,
            int maxAttempts,
            long retryInitialBackoffMillis,
            long retryMaxBackoffMillis,
            String progressPath,
            String reportPath,
            MemoryAnswerQualityEvaluationService.QualityThresholds thresholds) {
        public FormalEvaluationRequest {
            runMetadata = runMetadata == null
                    ? MemoryAnswerQualityEvaluationService.RunMetadata.empty()
                    : runMetadata;
            maxCases = maxCases <= 0 ? 120 : maxCases;
            minCases = minCases <= 0 ? 120 : minCases;
            maxInputTokens = maxInputTokens <= 0 ? 1_000_000 : maxInputTokens;
            estimatedOutputTokensPerCall = estimatedOutputTokensPerCall <= 0 ? 384 : estimatedOutputTokensPerCall;
            estimatedJudgeOutputTokensPerCall = estimatedJudgeOutputTokensPerCall <= 0 ? 256 : estimatedJudgeOutputTokensPerCall;
            maxEstimatedCostYuan = maxEstimatedCostYuan <= 0 ? 20.0 : maxEstimatedCostYuan;
            perCaseTimeoutMillis = perCaseTimeoutMillis <= 0 ? 180_000 : perCaseTimeoutMillis;
            maxAttempts = maxAttempts <= 0 ? DEFAULT_MAX_ATTEMPTS : maxAttempts;
            retryInitialBackoffMillis = Math.max(0, retryInitialBackoffMillis);
            retryMaxBackoffMillis = retryMaxBackoffMillis <= 0 ? 15_000 : retryMaxBackoffMillis;
            progressPath = hasText(progressPath)
                    ? progressPath
                    : Path.of("target", "memory-answer-quality-eval", runMetadata.runId() + ".progress.jsonl").toString();
            reportPath = hasText(reportPath)
                    ? reportPath
                    : Path.of("target", "memory-answer-quality-eval", runMetadata.runId() + ".json").toString();
            thresholds = thresholds == null
                    ? MemoryAnswerQualityEvaluationService.QualityThresholds.productionDefaults()
                    : thresholds;
        }

        static FormalEvaluationRequest defaults() {
            return new FormalEvaluationRequest(
                    false,
                    false,
                    MemoryAnswerQualityEvaluationService.RunMetadata.empty(),
                    120,
                    120,
                    1_000_000,
                    384,
                    256,
                    20.0,
                    true,
                    180_000,
                    true,
                    true,
                    3,
                    1_000,
                    15_000,
                    "",
                    "",
                    MemoryAnswerQualityEvaluationService.QualityThresholds.productionDefaults());
        }
    }

    public record EvaluationBudget(int selectedCases,
                                   int estimatedInputTokens,
                                   int estimatedOutputTokens,
                                   double estimatedCostYuan,
                                   int maxInputTokens,
                                   double maxEstimatedCostYuan) {
    }

    public record ProgressEnvelope(
            String runFingerprint,
            MemoryAnswerQualityEvaluationService.CaseExecution execution) {
        public ProgressEnvelope {
            runFingerprint = runFingerprint == null ? "" : runFingerprint;
        }
    }

    public record FormalEvaluationReport(
            RunStatus status,
            MemoryAnswerQualityEvaluationService.RunMetadata run,
            EvaluationBudget budget,
            List<String> blockReasons,
            int selectedCases,
            int evaluatedCases,
            int resumedCases,
            int retriedCases,
            int timedOutCases,
            String progressPath,
            String reportPath,
            MemoryAnswerQualityEvaluationService.EvaluationReport evaluationReport,
            List<MemoryAnswerQualityEvaluationService.CaseExecution> results,
            String startedAt,
            String completedAt) {
        public FormalEvaluationReport {
            blockReasons = blockReasons == null ? List.of() : List.copyOf(blockReasons);
            results = results == null ? List.of() : List.copyOf(results);
        }
    }
}
