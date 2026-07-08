package com.ainote.app.service;

import com.ainote.app.config.MemoryProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryAdvisorFormalEvaluationIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @EnabledIfEnvironmentVariable(named = "MEMORY_ADVISOR_FORMAL_EVAL_ENABLED", matches = "true")
    void runsBudgetedFormalEvaluationAgainstConfiguredModel() {
        String apiKey = value("DEEPSEEK_API_KEY", "");
        String modelName = value("DEEPSEEK_MODEL", "deepseek-chat");
        String baseUrl = value("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1");

        MemoryAdvisorFormalEvaluationService service = new MemoryAdvisorFormalEvaluationService(
                new MemoryAdvisorProductionQualityService(
                        new MemoryAdvisorReplayEvaluationService(),
                        new MemoryReplayEvaluationService(new MemoryCapturePolicy(), new MemoryCandidateExtractor())),
                costTrackingService(),
                new JiTokenCountEstimator(new JiTokenService()),
                OBJECT_MAPPER);
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases =
                MemoryReplayDatasetLoader.loadActiveDataset().cases();

        MemoryAdvisorFormalEvaluationService.FormalEvaluationRequest request = request(apiKey, modelName);
        MemorySignalAdvisor advisor = hasText(apiKey)
                ? realAdvisor(apiKey, modelName, baseUrl)
                : requestPayload -> MemorySignalAdvisor.AdvisorResult.unavailable(
                List.of("api_key_missing"), "api key missing");

        MemoryAdvisorFormalEvaluationService.FormalEvaluationReport report =
                service.run(cases, advisor, request);

        assertThat(report.reportPath()).isNotBlank();
        assertThat(Files.exists(Path.of(report.reportPath()))).isTrue();
        if (report.status() == MemoryAdvisorFormalEvaluationService.RunStatus.COMPLETED) {
            assertThat(report.readinessReport()).isNotNull();
            assertThat(report.readinessReport().run().modelName()).isEqualTo(modelName);
            assertThat(report.readinessReport().run().promptVersion())
                    .isEqualTo(LlmMemorySignalAdvisor.PROMPT_VERSION);
        } else {
            assertThat(report.blockReasons()).isNotEmpty();
            assertThat(report.readinessReport()).isNull();
        }
    }

    private MemoryAdvisorFormalEvaluationService.FormalEvaluationRequest request(
            String apiKey,
            String modelName) {
        return new MemoryAdvisorFormalEvaluationService.FormalEvaluationRequest(
                value("MEMORY_ADVISOR_FORMAL_EVAL_RUN_ID", "memory-advisor-formal-eval"),
                enabled(),
                hasText(apiKey),
                modelName,
                LlmMemorySignalAdvisor.PROMPT_VERSION,
                value("MEMORY_ADVISOR_FORMAL_EVAL_DATASET_VERSION", "active-replay-v2026-07-08"),
                intValue("MEMORY_ADVISOR_FORMAL_EVAL_MAX_CASES", 4000),
                intValue("MEMORY_ADVISOR_FORMAL_EVAL_MIN_CASES", 4000),
                intValue("MEMORY_ADVISOR_FORMAL_EVAL_MAX_INPUT_TOKENS", 2_000_000),
                intValue("MEMORY_ADVISOR_FORMAL_EVAL_OUTPUT_TOKENS_PER_CASE", 96),
                doubleValue("MEMORY_ADVISOR_FORMAL_EVAL_MAX_COST_YUAN", 20.0),
                booleanValue("MEMORY_ADVISOR_FORMAL_EVAL_REQUIRE_KNOWN_PRICE", true),
                value("MEMORY_ADVISOR_FORMAL_EVAL_REPORT_DIR", "target/memory-advisor-formal-eval"),
                thresholds());
    }

    private MemoryAdvisorProductionQualityService.AdvisorQualityThresholds thresholds() {
        MemoryAdvisorProductionQualityService.AdvisorQualityThresholds defaults =
                MemoryAdvisorProductionQualityService.AdvisorQualityThresholds.productionDefaults();
        return new MemoryAdvisorProductionQualityService.AdvisorQualityThresholds(
                intValue("MEMORY_ADVISOR_FORMAL_EVAL_GATE_MIN_CASES", defaults.minTotalCases()),
                doubleValue("MEMORY_ADVISOR_FORMAL_EVAL_GATE_MIN_AVAILABILITY", defaults.minAvailabilityRate()),
                doubleValue("MEMORY_ADVISOR_FORMAL_EVAL_GATE_MIN_CAPTURE_ACCURACY",
                        defaults.minCaptureDecisionAccuracy()),
                doubleValue("MEMORY_ADVISOR_FORMAL_EVAL_GATE_MAX_FALSE_POSITIVE",
                        defaults.maxFalsePositiveRate()),
                doubleValue("MEMORY_ADVISOR_FORMAL_EVAL_GATE_MAX_FALSE_NEGATIVE",
                        defaults.maxFalseNegativeRate()),
                doubleValue("MEMORY_ADVISOR_FORMAL_EVAL_GATE_MIN_TYPE_ACCURACY",
                        defaults.minMemoryTypeAccuracy()),
                doubleValue("MEMORY_ADVISOR_FORMAL_EVAL_GATE_MAX_SENSITIVE_FALSE_ALLOW",
                        defaults.maxSensitiveFalseAllowRate()),
                longValue("MEMORY_ADVISOR_FORMAL_EVAL_GATE_MAX_P95_MS", defaults.maxP95LatencyMillis()),
                doubleValue("MEMORY_ADVISOR_FORMAL_EVAL_GATE_MAX_BASELINE_REGRESSION",
                        defaults.maxDecisionAccuracyRegression()));
    }

    private MemorySignalAdvisor realAdvisor(String apiKey, String modelName, String baseUrl) {
        MemoryProperties properties = new MemoryProperties();
        properties.getCapture().getAdvisor().setEnabled(true);
        ChatModel chatModel = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.2)
                .maxTokens(256)
                .timeout(Duration.ofSeconds(120))
                .logRequests(false)
                .logResponses(false)
                .build();
        return new LlmMemorySignalAdvisor(properties, chatModel, OBJECT_MAPPER);
    }

    private CostTrackingService costTrackingService() {
        CostTrackingService costTrackingService = new CostTrackingService();
        costTrackingService.setPrices(Map.of(
                "deepseek-chat", price(0.001, 0.002),
                "deepseek-reasoner", price(0.004, 0.016)));
        return costTrackingService;
    }

    private static CostTrackingService.ModelPrice price(double input, double output) {
        CostTrackingService.ModelPrice price = new CostTrackingService.ModelPrice();
        price.setInput(input);
        price.setOutput(output);
        return price;
    }

    private static boolean enabled() {
        return booleanValue("MEMORY_ADVISOR_FORMAL_EVAL_ENABLED", false);
    }

    private static String value(String key, String defaultValue) {
        String value = System.getenv(key);
        if (!hasText(value)) {
            value = System.getProperty(key);
        }
        return hasText(value) ? value : defaultValue;
    }

    private static int intValue(String key, int defaultValue) {
        String value = value(key, "");
        return hasText(value) ? Integer.parseInt(value) : defaultValue;
    }

    private static long longValue(String key, long defaultValue) {
        String value = value(key, "");
        return hasText(value) ? Long.parseLong(value) : defaultValue;
    }

    private static double doubleValue(String key, double defaultValue) {
        String value = value(key, "");
        return hasText(value) ? Double.parseDouble(value) : defaultValue;
    }

    private static boolean booleanValue(String key, boolean defaultValue) {
        String value = value(key, "");
        return hasText(value) ? Boolean.parseBoolean(value) : defaultValue;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
