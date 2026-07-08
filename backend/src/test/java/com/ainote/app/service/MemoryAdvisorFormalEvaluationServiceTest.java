package com.ainote.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryAdvisorFormalEvaluationServiceTest {

    @TempDir
    Path reportDir;

    private MemoryAdvisorFormalEvaluationService service;

    @BeforeEach
    void setUp() {
        CostTrackingService costTrackingService = new CostTrackingService();
        costTrackingService.setPrices(java.util.Map.of("deepseek-chat", price(0.001, 0.002)));
        service = new MemoryAdvisorFormalEvaluationService(
                new MemoryAdvisorProductionQualityService(
                        new MemoryAdvisorReplayEvaluationService(),
                        new MemoryReplayEvaluationService(new MemoryCapturePolicy(), new MemoryCandidateExtractor())),
                costTrackingService,
                new JiTokenCountEstimator(new JiTokenService()),
                new ObjectMapper());
    }

    @Test
    void preflightBlocksDisabledLiveRunBeforeAdvisorIsCalled() {
        AtomicInteger calls = new AtomicInteger();
        MemorySignalAdvisor advisor = request -> {
            calls.incrementAndGet();
            return MemorySignalAdvisor.AdvisorResult.noCapture("none", 0.1, List.of(), "not memory");
        };

        MemoryAdvisorFormalEvaluationService.FormalEvaluationReport report = service.run(
                replayCases(),
                advisor,
                request(false, 10.0, 100000));

        assertThat(report.status()).isEqualTo(MemoryAdvisorFormalEvaluationService.RunStatus.BLOCKED);
        assertThat(report.blockReasons()).contains("formal_evaluation_disabled");
        assertThat(calls).hasValue(0);
        assertThat(Files.exists(Path.of(report.reportPath()))).isTrue();
    }

    @Test
    void preflightBlocksCostOverBudgetBeforeAdvisorIsCalled() {
        AtomicInteger calls = new AtomicInteger();
        MemorySignalAdvisor advisor = request -> {
            calls.incrementAndGet();
            return MemorySignalAdvisor.AdvisorResult.noCapture("none", 0.1, List.of(), "not memory");
        };

        MemoryAdvisorFormalEvaluationService.FormalEvaluationReport report = service.run(
                replayCases(),
                advisor,
                request(true, 0.000001, 100000));

        assertThat(report.status()).isEqualTo(MemoryAdvisorFormalEvaluationService.RunStatus.BLOCKED);
        assertThat(report.blockReasons()).contains("estimated_cost_exceeds_budget");
        assertThat(calls).hasValue(0);
    }

    @Test
    void budgetedRunInvokesAdvisorAndWritesCompletedReport() throws Exception {
        MemorySignalAdvisor advisor = request -> {
            if (request.userMessage().startsWith("remember:")) {
                return MemorySignalAdvisor.AdvisorResult.capture(
                        "preference",
                        0.99,
                        List.of("advisor_preference_signal"),
                        "explicit preference");
            }
            return MemorySignalAdvisor.AdvisorResult.noCapture("none", 0.1, List.of(), "not memory");
        };

        MemoryAdvisorFormalEvaluationService.FormalEvaluationReport report = service.run(
                replayCases(),
                advisor,
                request(true, 10.0, 100000));

        assertThat(report.status()).isEqualTo(MemoryAdvisorFormalEvaluationService.RunStatus.COMPLETED);
        assertThat(report.blockReasons()).isEmpty();
        assertThat(report.readinessReport()).isNotNull();
        assertThat(report.readinessReport().qualityGatePassed()).isTrue();
        assertThat(report.budget().estimatedCostYuan()).isGreaterThan(0.0);
        String reportJson = Files.readString(Path.of(report.reportPath()));
        assertThat(reportJson)
                .contains("\"status\"")
                .contains("\"COMPLETED\"")
                .doesNotContain("DEEPSEEK_API_KEY")
                .doesNotContain("secret-api-key");
    }

    private MemoryAdvisorFormalEvaluationService.FormalEvaluationRequest request(
            boolean enabled,
            double maxEstimatedCostYuan,
            int maxInputTokens) {
        return new MemoryAdvisorFormalEvaluationService.FormalEvaluationRequest(
                "formal-eval-test",
                enabled,
                true,
                "deepseek-chat",
                LlmMemorySignalAdvisor.PROMPT_VERSION,
                "unit-replay",
                10,
                1,
                maxInputTokens,
                96,
                maxEstimatedCostYuan,
                true,
                reportDir.toString(),
                relaxedThresholds());
    }

    private MemoryAdvisorProductionQualityService.AdvisorQualityThresholds relaxedThresholds() {
        return new MemoryAdvisorProductionQualityService.AdvisorQualityThresholds(
                1,
                1.0,
                1.0,
                0.0,
                0.0,
                1.0,
                0.0,
                1500,
                0.0);
    }

    private List<MemoryReplayEvaluationService.MemoryReplayCase> replayCases() {
        return List.of(
                replayCase("remember_preference", "remember: I prefer concise answers.", true, "preference"),
                replayCase("one_off", "summarize this note for the current reply only.", false, ""));
    }

    private MemoryReplayEvaluationService.MemoryReplayCase replayCase(
            String id,
            String userMessage,
            boolean expectedAllowed,
            String memoryType) {
        return new MemoryReplayEvaluationService.MemoryReplayCase(
                id,
                userMessage,
                "ok",
                expectedAllowed,
                expectedAllowed ? "ALLOW_EXPLICIT" : "DENY_TRANSIENT",
                memoryType,
                expectedAllowed ? false : null,
                expectedAllowed ? "explicit_memory" : "one_off_instruction",
                expectedAllowed ? List.of("explicit_remember") : List.of("one_off_scope"));
    }

    private static CostTrackingService.ModelPrice price(double input, double output) {
        CostTrackingService.ModelPrice price = new CostTrackingService.ModelPrice();
        price.setInput(input);
        price.setOutput(output);
        return price;
    }
}
