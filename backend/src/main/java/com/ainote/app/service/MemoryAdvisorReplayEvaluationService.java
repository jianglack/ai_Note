package com.ainote.app.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MemoryAdvisorReplayEvaluationService {

    public AdvisorEvaluationReport evaluate(List<MemoryReplayEvaluationService.MemoryReplayCase> cases,
                                            MemorySignalAdvisor advisor) {
        List<MemoryReplayEvaluationService.MemoryReplayCase> replayCases = cases == null ? List.of() : cases;
        MemorySignalAdvisor effectiveAdvisor = advisor == null ? MemorySignalAdvisor.disabled() : advisor;
        List<AdvisorCaseResult> results = replayCases.stream()
                .map(replayCase -> evaluateCase(replayCase, effectiveAdvisor))
                .toList();
        List<AdvisorCaseResult> failures = results.stream()
                .filter(result -> !result.passed())
                .toList();

        int total = results.size();
        int positiveCount = count(results, AdvisorCaseResult::expectedCaptureAllowed);
        int negativeCount = count(results, result -> !result.expectedCaptureAllowed());
        int memoryTypeCount = count(results, result -> result.expectedCaptureAllowed() && hasText(result.expectedMemoryType()));

        return new AdvisorEvaluationReport(
                total,
                positiveCount,
                negativeCount,
                ratio(count(results, AdvisorCaseResult::available), total),
                ratio(count(results, AdvisorCaseResult::captureDecisionMatches), total),
                ratio(count(results, result -> !result.expectedCaptureAllowed() && result.actualShouldCapture()),
                        negativeCount),
                ratio(count(results, result -> result.expectedCaptureAllowed() && !result.actualShouldCapture()),
                        positiveCount),
                ratio(count(results, result -> result.expectedCaptureAllowed()
                                && hasText(result.expectedMemoryType())
                                && result.memoryTypeMatches()),
                        memoryTypeCount),
                List.copyOf(failures),
                List.copyOf(results));
    }

    private AdvisorCaseResult evaluateCase(MemoryReplayEvaluationService.MemoryReplayCase replayCase,
                                           MemorySignalAdvisor advisor) {
        MemoryCapturePolicy.CaptureRequest request = new MemoryCapturePolicy.CaptureRequest(
                "user-1",
                replayCase.userMessage(),
                replayCase.assistantOutput());
        MemorySignalAdvisor.AdvisorResult advisorResult = advisor.advise(request);
        boolean available = advisorResult != null && advisorResult.available();
        boolean actualShouldCapture = available && advisorResult.shouldCapture();
        String actualMemoryType = advisorResult == null ? "none" : advisorResult.memoryType();

        List<String> messages = new ArrayList<>();
        boolean captureDecisionMatches = available && actualShouldCapture == replayCase.expectedCaptureAllowed();
        if (!available) {
            messages.add("advisor unavailable");
        }
        if (actualShouldCapture != replayCase.expectedCaptureAllowed()) {
            messages.add("shouldCapture expected=" + replayCase.expectedCaptureAllowed()
                    + " actual=" + actualShouldCapture);
        }

        boolean memoryTypeMatches = !replayCase.expectedCaptureAllowed()
                || !hasText(replayCase.expectedMemoryType())
                || replayCase.expectedMemoryType().equals(actualMemoryType);
        if (!memoryTypeMatches) {
            messages.add("memoryType expected=" + replayCase.expectedMemoryType()
                    + " actual=" + actualMemoryType);
        }

        return new AdvisorCaseResult(
                replayCase.id(),
                messages.isEmpty(),
                List.copyOf(messages),
                replayCase.expectedCaptureAllowed(),
                actualShouldCapture,
                replayCase.expectedMemoryType(),
                actualMemoryType,
                available,
                captureDecisionMatches,
                memoryTypeMatches);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static int count(List<AdvisorCaseResult> results, ResultPredicate predicate) {
        int count = 0;
        for (AdvisorCaseResult result : results) {
            if (predicate.test(result)) {
                count++;
            }
        }
        return count;
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 1.0 : (double) numerator / denominator;
    }

    @FunctionalInterface
    private interface ResultPredicate {
        boolean test(AdvisorCaseResult result);
    }

    public record AdvisorEvaluationReport(int totalCases,
                                          int shouldRememberCases,
                                          int shouldNotRememberCases,
                                          double availabilityRate,
                                          double captureDecisionAccuracy,
                                          double falsePositiveRate,
                                          double falseNegativeRate,
                                          double memoryTypeAccuracy,
                                          List<AdvisorCaseResult> failures,
                                          List<AdvisorCaseResult> results) {
        public AdvisorEvaluationReport {
            failures = failures == null ? List.of() : List.copyOf(failures);
            results = results == null ? List.of() : List.copyOf(results);
        }
    }

    public record AdvisorCaseResult(String id,
                                    boolean passed,
                                    List<String> messages,
                                    boolean expectedCaptureAllowed,
                                    boolean actualShouldCapture,
                                    String expectedMemoryType,
                                    String actualMemoryType,
                                    boolean available,
                                    boolean captureDecisionMatches,
                                    boolean memoryTypeMatches) {
        public AdvisorCaseResult {
            messages = messages == null ? List.of() : List.copyOf(messages);
            expectedMemoryType = expectedMemoryType == null ? "" : expectedMemoryType;
            actualMemoryType = actualMemoryType == null ? "none" : actualMemoryType;
        }
    }
}
