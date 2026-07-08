package com.ainote.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class MemoryAdvisorFormalEvaluationService {

    private static final String DEFAULT_RUN_ID = "memory-advisor-formal-eval";
    private static final String DEFAULT_REPORT_DIRECTORY = "target/memory-advisor-formal-eval";
    private static final int PROMPT_OVERHEAD_TOKENS_PER_CASE = 180;

    private final MemoryAdvisorProductionQualityService productionQualityService;
    private final CostTrackingService costTrackingService;
    private final JiTokenCountEstimator tokenCountEstimator;
    private final ObjectMapper objectMapper;

    public MemoryAdvisorFormalEvaluationService(
            MemoryAdvisorProductionQualityService productionQualityService,
            CostTrackingService costTrackingService,
            JiTokenCountEstimator tokenCountEstimator,
            ObjectMapper objectMapper) {
        this.productionQualityService = productionQualityService;
        this.costTrackingService = costTrackingService;
        this.tokenCountEstimator = tokenCountEstimator;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public FormalEvaluationReport run(List<MemoryReplayEvaluationService.MemoryReplayCase> cases,
                                      MemorySignalAdvisor advisor,
                                      FormalEvaluationRequest request) {
        FormalEvaluationReport preflightReport = preflight(cases, request, false);
        FormalEvaluationRequest safeRequest = request == null ? FormalEvaluationRequest.defaults() : request;
        Path reportPath = Path.of(preflightReport.reportPath());

        if (preflightReport.status() == RunStatus.BLOCKED) {
            FormalEvaluationReport report = new FormalEvaluationReport(
                    RunStatus.BLOCKED,
                    safeRequest.runId(),
                    safeRequest.modelName(),
                    safeRequest.promptVersion(),
                    safeRequest.datasetVersion(),
                    preflightReport.budget(),
                    preflightReport.blockReasons(),
                    null,
                    reportPath.toString());
            writeReport(reportPath, report);
            return report;
        }

        List<MemoryReplayEvaluationService.MemoryReplayCase> replayCases = cases == null ? List.of() : cases;
        List<MemoryReplayEvaluationService.MemoryReplayCase> selectedCases = selectCases(replayCases, safeRequest.maxCases());
        MemoryAdvisorProductionQualityService.AdvisorReadinessReport readinessReport =
                productionQualityService.evaluate(
                        selectedCases,
                        advisor,
                        new MemoryAdvisorProductionQualityService.AdvisorEvaluationRun(
                                safeRequest.runId(),
                                "llm-memory-signal-advisor",
                                safeRequest.modelName(),
                                safeRequest.promptVersion(),
                                safeRequest.datasetVersion(),
                                safeRequest.qualityThresholds()));
        FormalEvaluationReport report = new FormalEvaluationReport(
                RunStatus.COMPLETED,
                safeRequest.runId(),
                safeRequest.modelName(),
                safeRequest.promptVersion(),
                safeRequest.datasetVersion(),
                preflightReport.budget(),
                List.of(),
                readinessReport,
                reportPath.toString());
        writeReport(reportPath, report);
        return report;
    }

    public FormalEvaluationReport preflight(List<MemoryReplayEvaluationService.MemoryReplayCase> cases,
                                            FormalEvaluationRequest request) {
        return preflight(cases, request, true);
    }

    private FormalEvaluationReport preflight(List<MemoryReplayEvaluationService.MemoryReplayCase> cases,
                                             FormalEvaluationRequest request,
                                             boolean writeReport) {
        FormalEvaluationRequest safeRequest = request == null ? FormalEvaluationRequest.defaults() : request;
        List<MemoryReplayEvaluationService.MemoryReplayCase> replayCases = cases == null ? List.of() : cases;
        List<MemoryReplayEvaluationService.MemoryReplayCase> selectedCases = selectCases(replayCases, safeRequest.maxCases());
        FormalEvaluationBudget budget = estimateBudget(selectedCases, safeRequest);
        List<String> blockReasons = blockReasons(selectedCases, safeRequest, budget);
        Path reportPath = reportPath(safeRequest);
        FormalEvaluationReport report = new FormalEvaluationReport(
                blockReasons.isEmpty() ? RunStatus.READY : RunStatus.BLOCKED,
                safeRequest.runId(),
                safeRequest.modelName(),
                safeRequest.promptVersion(),
                safeRequest.datasetVersion(),
                budget,
                blockReasons,
                null,
                reportPath.toString());
        if (writeReport) {
            writeReport(reportPath, report);
        }
        return report;
    }

    private List<MemoryReplayEvaluationService.MemoryReplayCase> selectCases(
            List<MemoryReplayEvaluationService.MemoryReplayCase> replayCases,
            int maxCases) {
        if (maxCases <= 0 || replayCases.size() <= maxCases) {
            return List.copyOf(replayCases);
        }
        return List.copyOf(replayCases.subList(0, maxCases));
    }

    private FormalEvaluationBudget estimateBudget(
            List<MemoryReplayEvaluationService.MemoryReplayCase> selectedCases,
            FormalEvaluationRequest request) {
        int estimatedInputTokens = selectedCases.stream()
                .mapToInt(this::estimateInputTokens)
                .sum();
        int estimatedOutputTokens = Math.max(0, selectedCases.size() * request.estimatedOutputTokensPerCase());
        double estimatedCostYuan = costTrackingService == null
                ? 0.0
                : costTrackingService.calculateCost(request.modelName(), estimatedInputTokens, estimatedOutputTokens);
        return new FormalEvaluationBudget(
                request.maxCases(),
                selectedCases.size(),
                estimatedInputTokens,
                estimatedOutputTokens,
                estimatedCostYuan,
                request.maxInputTokens(),
                request.maxEstimatedCostYuan(),
                request.estimatedOutputTokensPerCase(),
                request.modelName());
    }

    private int estimateInputTokens(MemoryReplayEvaluationService.MemoryReplayCase replayCase) {
        if (replayCase == null) {
            return PROMPT_OVERHEAD_TOKENS_PER_CASE;
        }
        String text = "Memory advisor prompt version: " + LlmMemorySignalAdvisor.PROMPT_VERSION
                + "\nUSER_MESSAGE:\n" + replayCase.userMessage()
                + "\nASSISTANT_OUTPUT:\n" + replayCase.assistantOutput();
        int contentTokens = tokenCountEstimator == null
                ? fallbackTokenEstimate(text)
                : tokenCountEstimator.estimateTokenCountInText(text);
        return Math.max(1, contentTokens) + PROMPT_OVERHEAD_TOKENS_PER_CASE;
    }

    private List<String> blockReasons(List<MemoryReplayEvaluationService.MemoryReplayCase> selectedCases,
                                      FormalEvaluationRequest request,
                                      FormalEvaluationBudget budget) {
        List<String> reasons = new ArrayList<>();
        if (!request.enabled()) {
            reasons.add("formal_evaluation_disabled");
        }
        if (!request.apiKeyConfigured()) {
            reasons.add("api_key_missing");
        }
        if (!hasText(request.modelName())) {
            reasons.add("model_name_missing");
        }
        if (!hasText(request.promptVersion())) {
            reasons.add("prompt_version_missing");
        }
        if (!hasText(request.datasetVersion())) {
            reasons.add("dataset_version_missing");
        }
        if (selectedCases.size() < request.minRequiredCases()) {
            reasons.add("selected_cases_below_required_minimum");
        }
        if (budget.estimatedInputTokens() > request.maxInputTokens()) {
            reasons.add("estimated_input_tokens_exceed_budget");
        }
        if (budget.estimatedCostYuan() > request.maxEstimatedCostYuan()) {
            reasons.add("estimated_cost_exceeds_budget");
        }
        if (request.requireKnownModelPrice()
                && (budget.estimatedInputTokens() > 0 || budget.estimatedOutputTokens() > 0)
                && budget.estimatedCostYuan() <= 0.0) {
            reasons.add("model_price_missing");
        }
        return List.copyOf(reasons);
    }

    private Path reportPath(FormalEvaluationRequest request) {
        return Path.of(request.reportDirectory(), sanitizeFileName(request.runId()) + ".json");
    }

    private void writeReport(Path reportPath, FormalEvaluationReport report) {
        try {
            Files.createDirectories(reportPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write memory advisor formal evaluation report: " + reportPath, e);
        }
    }

    private static String sanitizeFileName(String value) {
        String sanitized = safe(value).trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.isBlank() ? DEFAULT_RUN_ID : sanitized;
    }

    private static int fallbackTokenEstimate(String text) {
        return Math.max(1, (int) Math.ceil(safe(text).length() / 4.0));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public enum RunStatus {
        READY,
        BLOCKED,
        COMPLETED
    }

    public record FormalEvaluationRequest(String runId,
                                          boolean enabled,
                                          boolean apiKeyConfigured,
                                          String modelName,
                                          String promptVersion,
                                          String datasetVersion,
                                          int maxCases,
                                          int minRequiredCases,
                                          int maxInputTokens,
                                          int estimatedOutputTokensPerCase,
                                          double maxEstimatedCostYuan,
                                          boolean requireKnownModelPrice,
                                          String reportDirectory,
                                          MemoryAdvisorProductionQualityService.AdvisorQualityThresholds qualityThresholds) {
        public FormalEvaluationRequest {
            runId = hasText(runId) ? runId : DEFAULT_RUN_ID;
            modelName = safe(modelName);
            promptVersion = safe(promptVersion);
            datasetVersion = safe(datasetVersion);
            maxCases = Math.max(0, maxCases);
            minRequiredCases = Math.max(0, minRequiredCases);
            maxInputTokens = Math.max(0, maxInputTokens);
            estimatedOutputTokensPerCase = Math.max(0, estimatedOutputTokensPerCase);
            maxEstimatedCostYuan = Math.max(0.0, maxEstimatedCostYuan);
            reportDirectory = hasText(reportDirectory) ? reportDirectory : DEFAULT_REPORT_DIRECTORY;
            qualityThresholds = qualityThresholds == null
                    ? MemoryAdvisorProductionQualityService.AdvisorQualityThresholds.productionDefaults()
                    : qualityThresholds;
        }

        static FormalEvaluationRequest defaults() {
            return new FormalEvaluationRequest(
                    DEFAULT_RUN_ID,
                    false,
                    false,
                    "",
                    "",
                    "",
                    0,
                    1,
                    0,
                    96,
                    0.0,
                    true,
                    DEFAULT_REPORT_DIRECTORY,
                    MemoryAdvisorProductionQualityService.AdvisorQualityThresholds.productionDefaults());
        }
    }

    public record FormalEvaluationBudget(int requestedCases,
                                         int selectedCases,
                                         int estimatedInputTokens,
                                         int estimatedOutputTokens,
                                         double estimatedCostYuan,
                                         int maxInputTokens,
                                         double maxEstimatedCostYuan,
                                         int estimatedOutputTokensPerCase,
                                         String modelName) {
        public FormalEvaluationBudget {
            requestedCases = Math.max(0, requestedCases);
            selectedCases = Math.max(0, selectedCases);
            estimatedInputTokens = Math.max(0, estimatedInputTokens);
            estimatedOutputTokens = Math.max(0, estimatedOutputTokens);
            estimatedCostYuan = Math.max(0.0, estimatedCostYuan);
            maxInputTokens = Math.max(0, maxInputTokens);
            maxEstimatedCostYuan = Math.max(0.0, maxEstimatedCostYuan);
            estimatedOutputTokensPerCase = Math.max(0, estimatedOutputTokensPerCase);
            modelName = safe(modelName).toLowerCase(Locale.ROOT);
        }
    }

    public record FormalEvaluationReport(RunStatus status,
                                         String runId,
                                         String modelName,
                                         String promptVersion,
                                         String datasetVersion,
                                         FormalEvaluationBudget budget,
                                         List<String> blockReasons,
                                         MemoryAdvisorProductionQualityService.AdvisorReadinessReport readinessReport,
                                         String reportPath) {
        public FormalEvaluationReport {
            status = status == null ? RunStatus.BLOCKED : status;
            runId = hasText(runId) ? runId : DEFAULT_RUN_ID;
            modelName = safe(modelName);
            promptVersion = safe(promptVersion);
            datasetVersion = safe(datasetVersion);
            blockReasons = blockReasons == null ? List.of() : List.copyOf(blockReasons);
            reportPath = safe(reportPath);
        }
    }
}
