package com.ainote.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryAdvisorFormalBatchEvaluationServiceTest {

    @TempDir
    Path reportDir;

    private MemoryAdvisorFormalBatchEvaluationService service;

    @BeforeEach
    void setUp() {
        CostTrackingService costTrackingService = new CostTrackingService();
        costTrackingService.setPrices(Map.of("deepseek-chat", price(0.001, 0.002)));
        MemoryAdvisorProductionQualityService productionQualityService = new MemoryAdvisorProductionQualityService(
                new MemoryAdvisorReplayEvaluationService(),
                new MemoryReplayEvaluationService(new MemoryCapturePolicy(), new MemoryCandidateExtractor()));
        service = new MemoryAdvisorFormalBatchEvaluationService(
                new MemoryAdvisorFormalEvaluationService(
                        productionQualityService,
                        costTrackingService,
                        new JiTokenCountEstimator(new JiTokenService()),
                        new ObjectMapper()),
                productionQualityService,
                new ObjectMapper());
    }

    @Test
    void writesProgressAndResumesCompletedCasesWithoutCallingAdvisorAgain() throws Exception {
        Path progressPath = reportDir.resolve("progress.jsonl");
        Path reportPath = reportDir.resolve("report.json");
        AtomicInteger firstRunCalls = new AtomicInteger();

        MemoryAdvisorFormalBatchEvaluationService.BatchEvaluationReport firstReport = service.run(
                replayCases(),
                matchingAdvisor(firstRunCalls),
                request(progressPath, reportPath, true, 1000));

        assertThat(firstReport.status()).isEqualTo(MemoryAdvisorFormalBatchEvaluationService.BatchRunStatus.COMPLETED);
        assertThat(firstReport.evaluatedCases()).isEqualTo(2);
        assertThat(firstReport.resumedCases()).isZero();
        assertThat(firstReport.timedOutCases()).isZero();
        assertThat(firstRunCalls).hasValue(2);
        assertThat(Files.readAllLines(progressPath)).hasSize(2);
        assertThat(Files.exists(reportPath)).isTrue();

        AtomicInteger secondRunCalls = new AtomicInteger();
        MemoryAdvisorFormalBatchEvaluationService.BatchEvaluationReport secondReport = service.run(
                replayCases(),
                matchingAdvisor(secondRunCalls),
                request(progressPath, reportPath, true, 1000));

        assertThat(secondReport.status()).isEqualTo(MemoryAdvisorFormalBatchEvaluationService.BatchRunStatus.COMPLETED);
        assertThat(secondReport.evaluatedCases()).isEqualTo(2);
        assertThat(secondReport.resumedCases()).isEqualTo(2);
        assertThat(secondRunCalls).hasValue(0);
        assertThat(secondReport.readinessReport().qualityGatePassed()).isTrue();
    }

    @Test
    void marksTimedOutCaseUnavailableAndContinuesToNextCase() throws Exception {
        Path progressPath = reportDir.resolve("timeout-progress.jsonl");
        Path reportPath = reportDir.resolve("timeout-report.json");
        AtomicInteger calls = new AtomicInteger();
        MemorySignalAdvisor advisor = request -> {
            if (calls.getAndIncrement() == 0) {
                sleep(250);
                return MemorySignalAdvisor.AdvisorResult.capture(
                        "preference",
                        0.99,
                        List.of("advisor_preference_signal"),
                        "too late");
            }
            return MemorySignalAdvisor.AdvisorResult.noCapture("none", 0.1, List.of(), "not memory");
        };

        MemoryAdvisorFormalBatchEvaluationService.BatchEvaluationReport report = service.run(
                replayCases(),
                advisor,
                request(progressPath, reportPath, false, 50));

        assertThat(report.status()).isEqualTo(MemoryAdvisorFormalBatchEvaluationService.BatchRunStatus.COMPLETED);
        assertThat(report.evaluatedCases()).isEqualTo(2);
        assertThat(report.timedOutCases()).isEqualTo(1);
        assertThat(report.readinessReport().qualityGatePassed()).isFalse();
        assertThat(report.readinessReport().failureReport().totalFailures()).isGreaterThanOrEqualTo(1);
        assertThat(Files.readAllLines(progressPath)).hasSize(2);
        String reportJson = Files.readString(reportPath);
        assertThat(reportJson)
                .contains("\"timedOutCases\"")
                .contains("\"TIMEOUT\"");
    }

    private MemoryAdvisorFormalBatchEvaluationService.BatchEvaluationRequest request(
            Path progressPath,
            Path reportPath,
            boolean resume,
            long perCaseTimeoutMillis) {
        return new MemoryAdvisorFormalBatchEvaluationService.BatchEvaluationRequest(
                formalRequest(),
                1,
                perCaseTimeoutMillis,
                resume,
                progressPath.toString(),
                reportPath.toString());
    }

    private MemoryAdvisorFormalEvaluationService.FormalEvaluationRequest formalRequest() {
        return new MemoryAdvisorFormalEvaluationService.FormalEvaluationRequest(
                "batch-eval-test",
                true,
                true,
                "deepseek-chat",
                LlmMemorySignalAdvisor.PROMPT_VERSION,
                "unit-replay",
                10,
                1,
                100000,
                96,
                10.0,
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

    private MemorySignalAdvisor matchingAdvisor(AtomicInteger calls) {
        return request -> {
            calls.incrementAndGet();
            if (request.userMessage().startsWith("remember:")) {
                return MemorySignalAdvisor.AdvisorResult.capture(
                        "preference",
                        0.99,
                        List.of("advisor_preference_signal"),
                        "explicit preference");
            }
            return MemorySignalAdvisor.AdvisorResult.noCapture("none", 0.1, List.of(), "not memory");
        };
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

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
