package com.ainote.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MemoryAdvisorFormalBatchEvaluationService {

    private static final int DEFAULT_BATCH_SIZE = 25;
    private static final long DEFAULT_PER_CASE_TIMEOUT_MILLIS = 180_000L;

    private final MemoryAdvisorFormalEvaluationService formalEvaluationService;
    private final MemoryAdvisorProductionQualityService productionQualityService;
    private final ObjectMapper objectMapper;

    public MemoryAdvisorFormalBatchEvaluationService(
            MemoryAdvisorFormalEvaluationService formalEvaluationService,
            MemoryAdvisorProductionQualityService productionQualityService,
            ObjectMapper objectMapper) {
        this.formalEvaluationService = formalEvaluationService;
        this.productionQualityService = productionQualityService;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public BatchEvaluationReport run(List<MemoryReplayEvaluationService.MemoryReplayCase> cases,
                                     MemorySignalAdvisor advisor,
                                     BatchEvaluationRequest request) {
        BatchEvaluationRequest safeRequest = request == null ? BatchEvaluationRequest.defaults() : request;
        List<MemoryReplayEvaluationService.MemoryReplayCase> replayCases = cases == null ? List.of() : cases;
        List<MemoryReplayEvaluationService.MemoryReplayCase> selectedCases =
                selectCases(replayCases, safeRequest.formalRequest().maxCases());
        Path progressPath = Path.of(safeRequest.progressPath());
        Path reportPath = Path.of(safeRequest.reportPath());

        MemoryAdvisorFormalEvaluationService.FormalEvaluationReport preflightReport =
                formalEvaluationService.preflight(replayCases, safeRequest.formalRequest());
        if (preflightReport.status() == MemoryAdvisorFormalEvaluationService.RunStatus.BLOCKED) {
            BatchEvaluationReport blockedReport = new BatchEvaluationReport(
                    BatchRunStatus.BLOCKED,
                    safeRequest.formalRequest().runId(),
                    selectedCases.size(),
                    0,
                    0,
                    0,
                    progressPath.toString(),
                    reportPath.toString(),
                    preflightReport,
                    null,
                    List.of(),
                    Instant.now().toString(),
                    Instant.now().toString());
            writeReport(reportPath, blockedReport);
            return blockedReport;
        }

        String startedAt = Instant.now().toString();
        Map<String, ProgressEntry> progressByCaseId = loadProgress(progressPath, safeRequest.resume());
        int resumedCases = 0;
        ExecutorService executor = Executors.newCachedThreadPool(daemonThreadFactory());
        try {
            for (MemoryReplayEvaluationService.MemoryReplayCase replayCase : selectedCases) {
                if (progressByCaseId.containsKey(replayCase.id())) {
                    resumedCases++;
                    continue;
                }
                ProgressEntry entry = evaluateCase(
                        replayCase,
                        advisor == null ? MemorySignalAdvisor.disabled() : advisor,
                        safeRequest.perCaseTimeoutMillis(),
                        executor);
                progressByCaseId.put(replayCase.id(), entry);
                appendProgress(progressPath, entry);
            }
        } finally {
            executor.shutdownNow();
        }

        List<ProgressEntry> orderedProgress = selectedCases.stream()
                .map(MemoryReplayEvaluationService.MemoryReplayCase::id)
                .map(progressByCaseId::get)
                .filter(entry -> entry != null)
                .toList();
        MemoryAdvisorProductionQualityService.AdvisorReadinessReport readinessReport =
                productionQualityService.evaluate(
                        selectedCases,
                        replayAdvisor(selectedCases, progressByCaseId),
                        new MemoryAdvisorProductionQualityService.AdvisorEvaluationRun(
                                safeRequest.formalRequest().runId(),
                                "llm-memory-signal-advisor-batch",
                                safeRequest.formalRequest().modelName(),
                                safeRequest.formalRequest().promptVersion(),
                                safeRequest.formalRequest().datasetVersion(),
                                safeRequest.formalRequest().qualityThresholds()));

        BatchEvaluationReport report = new BatchEvaluationReport(
                BatchRunStatus.COMPLETED,
                safeRequest.formalRequest().runId(),
                selectedCases.size(),
                orderedProgress.size(),
                resumedCases,
                (int) orderedProgress.stream().filter(entry -> entry.status() == CaseStatus.TIMEOUT).count(),
                progressPath.toString(),
                reportPath.toString(),
                preflightReport,
                readinessReport,
                orderedProgress,
                startedAt,
                Instant.now().toString());
        writeReport(reportPath, report);
        return report;
    }

    private ProgressEntry evaluateCase(MemoryReplayEvaluationService.MemoryReplayCase replayCase,
                                       MemorySignalAdvisor advisor,
                                       long perCaseTimeoutMillis,
                                       ExecutorService executor) {
        MemoryCapturePolicy.CaptureRequest request = new MemoryCapturePolicy.CaptureRequest(
                "user-1",
                replayCase.userMessage(),
                replayCase.assistantOutput());
        long startedAt = System.nanoTime();
        Future<MemorySignalAdvisor.AdvisorResult> future = executor.submit(() -> advisor.advise(request));
        try {
            MemorySignalAdvisor.AdvisorResult result =
                    future.get(Math.max(1, perCaseTimeoutMillis), TimeUnit.MILLISECONDS);
            long latencyMillis = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
            return ProgressEntry.from(replayCase.id(), CaseStatus.COMPLETED, result, latencyMillis);
        } catch (TimeoutException e) {
            future.cancel(true);
            long latencyMillis = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
            return ProgressEntry.from(
                    replayCase.id(),
                    CaseStatus.TIMEOUT,
                    MemorySignalAdvisor.AdvisorResult.unavailable(List.of("advisor_timeout"), "TIMEOUT"),
                    latencyMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ProgressEntry.from(
                    replayCase.id(),
                    CaseStatus.ERROR,
                    MemorySignalAdvisor.AdvisorResult.unavailable(List.of("advisor_interrupted"), "InterruptedException"),
                    Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L));
        } catch (ExecutionException e) {
            return ProgressEntry.from(
                    replayCase.id(),
                    CaseStatus.ERROR,
                    MemorySignalAdvisor.AdvisorResult.unavailable(
                            List.of("advisor_exception"),
                            e.getCause() == null ? e.getClass().getSimpleName() : e.getCause().getClass().getSimpleName()),
                    Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L));
        }
    }

    private MemorySignalAdvisor replayAdvisor(
            List<MemoryReplayEvaluationService.MemoryReplayCase> selectedCases,
            Map<String, ProgressEntry> progressByCaseId) {
        Map<String, String> caseIdByUserMessage = selectedCases.stream()
                .collect(Collectors.toMap(
                        MemoryReplayEvaluationService.MemoryReplayCase::userMessage,
                        MemoryReplayEvaluationService.MemoryReplayCase::id,
                        (left, right) -> left,
                        LinkedHashMap::new));
        return request -> {
            if (request == null) {
                return MemorySignalAdvisor.AdvisorResult.unavailable(List.of("advisor_missing_request"), "missing request");
            }
            String caseId = caseIdByUserMessage.get(request.userMessage());
            ProgressEntry entry = caseId == null ? null : progressByCaseId.get(caseId);
            if (entry == null) {
                return MemorySignalAdvisor.AdvisorResult.unavailable(List.of("advisor_missing_progress"), "missing progress");
            }
            return new MemorySignalAdvisor.AdvisorResult(
                    entry.available(),
                    entry.shouldCapture(),
                    entry.memoryType(),
                    entry.confidence(),
                    entry.signals(),
                    entry.reason());
        };
    }

    private Map<String, ProgressEntry> loadProgress(Path progressPath, boolean resume) {
        if (!resume || !Files.exists(progressPath)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, ProgressEntry> entries = new LinkedHashMap<>();
            for (String line : Files.readAllLines(progressPath)) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                ProgressEntry entry = objectMapper.readValue(line, ProgressEntry.class);
                entries.put(entry.caseId(), entry);
            }
            return entries;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read memory advisor batch progress: " + progressPath, e);
        }
    }

    private void appendProgress(Path progressPath, ProgressEntry entry) {
        try {
            Files.createDirectories(progressPath.getParent());
            Files.writeString(
                    progressPath,
                    objectMapper.writeValueAsString(entry) + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to append memory advisor batch progress: " + progressPath, e);
        }
    }

    private void writeReport(Path reportPath, BatchEvaluationReport report) {
        try {
            Files.createDirectories(reportPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write memory advisor batch report: " + reportPath, e);
        }
    }

    private List<MemoryReplayEvaluationService.MemoryReplayCase> selectCases(
            List<MemoryReplayEvaluationService.MemoryReplayCase> replayCases,
            int maxCases) {
        if (maxCases <= 0 || replayCases.size() <= maxCases) {
            return List.copyOf(replayCases);
        }
        return List.copyOf(replayCases.subList(0, maxCases));
    }

    private static ThreadFactory daemonThreadFactory() {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "memory-advisor-batch-eval-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    public enum BatchRunStatus {
        BLOCKED,
        COMPLETED
    }

    public enum CaseStatus {
        COMPLETED,
        TIMEOUT,
        ERROR
    }

    public record BatchEvaluationRequest(
            MemoryAdvisorFormalEvaluationService.FormalEvaluationRequest formalRequest,
            int batchSize,
            long perCaseTimeoutMillis,
            boolean resume,
            String progressPath,
            String reportPath) {
        public BatchEvaluationRequest {
            formalRequest = formalRequest == null
                    ? MemoryAdvisorFormalEvaluationService.FormalEvaluationRequest.defaults()
                    : formalRequest;
            batchSize = batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
            perCaseTimeoutMillis = perCaseTimeoutMillis <= 0
                    ? DEFAULT_PER_CASE_TIMEOUT_MILLIS
                    : perCaseTimeoutMillis;
            progressPath = hasText(progressPath)
                    ? progressPath
                    : Path.of(formalRequest.reportDirectory(), formalRequest.runId() + ".progress.jsonl").toString();
            reportPath = hasText(reportPath)
                    ? reportPath
                    : Path.of(formalRequest.reportDirectory(), formalRequest.runId() + ".batch.json").toString();
        }

        static BatchEvaluationRequest defaults() {
            MemoryAdvisorFormalEvaluationService.FormalEvaluationRequest formalRequest =
                    MemoryAdvisorFormalEvaluationService.FormalEvaluationRequest.defaults();
            return new BatchEvaluationRequest(
                    formalRequest,
                    DEFAULT_BATCH_SIZE,
                    DEFAULT_PER_CASE_TIMEOUT_MILLIS,
                    true,
                    Path.of(formalRequest.reportDirectory(), formalRequest.runId() + ".progress.jsonl").toString(),
                    Path.of(formalRequest.reportDirectory(), formalRequest.runId() + ".batch.json").toString());
        }
    }

    public record BatchEvaluationReport(BatchRunStatus status,
                                        String runId,
                                        int selectedCases,
                                        int evaluatedCases,
                                        int resumedCases,
                                        int timedOutCases,
                                        String progressPath,
                                        String reportPath,
                                        MemoryAdvisorFormalEvaluationService.FormalEvaluationReport preflightReport,
                                        MemoryAdvisorProductionQualityService.AdvisorReadinessReport readinessReport,
                                        List<ProgressEntry> results,
                                        String startedAt,
                                        String completedAt) {
        public BatchEvaluationReport {
            status = status == null ? BatchRunStatus.BLOCKED : status;
            runId = safe(runId);
            selectedCases = Math.max(0, selectedCases);
            evaluatedCases = Math.max(0, evaluatedCases);
            resumedCases = Math.max(0, resumedCases);
            timedOutCases = Math.max(0, timedOutCases);
            progressPath = safe(progressPath);
            reportPath = safe(reportPath);
            results = results == null ? List.of() : List.copyOf(results);
            startedAt = safe(startedAt);
            completedAt = safe(completedAt);
        }
    }

    public record ProgressEntry(String caseId,
                                CaseStatus status,
                                boolean available,
                                boolean shouldCapture,
                                String memoryType,
                                double confidence,
                                List<String> signals,
                                String reason,
                                long latencyMillis,
                                String completedAt) {
        public ProgressEntry {
            caseId = safe(caseId);
            status = status == null ? CaseStatus.ERROR : status;
            memoryType = memoryType == null ? "none" : memoryType;
            signals = signals == null ? List.of() : List.copyOf(signals);
            reason = safe(reason);
            latencyMillis = Math.max(0L, latencyMillis);
            completedAt = safe(completedAt);
        }

        static ProgressEntry from(String caseId,
                                  CaseStatus status,
                                  MemorySignalAdvisor.AdvisorResult result,
                                  long latencyMillis) {
            MemorySignalAdvisor.AdvisorResult safeResult = result == null
                    ? MemorySignalAdvisor.AdvisorResult.unavailable(List.of("advisor_null_result"), "null advisor result")
                    : result;
            return new ProgressEntry(
                    caseId,
                    status,
                    safeResult.available(),
                    safeResult.shouldCapture(),
                    safeResult.memoryType(),
                    safeResult.confidence(),
                    safeResult.signals(),
                    safeResult.reason(),
                    latencyMillis,
                    Instant.now().toString());
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
