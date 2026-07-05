package com.ainote.app.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MemoryReplayEvaluationService {

    private final MemoryCapturePolicy capturePolicy;
    private final MemoryCandidateExtractor candidateExtractor;

    @Autowired
    public MemoryReplayEvaluationService(MemoryCapturePolicy capturePolicy,
                                         MemoryCandidateExtractor candidateExtractor) {
        this.capturePolicy = capturePolicy;
        this.candidateExtractor = candidateExtractor;
    }

    public EvaluationReport evaluate(List<MemoryReplayCase> cases) {
        List<MemoryReplayCase> replayCases = cases == null ? List.of() : cases;
        List<CaseResult> results = replayCases.stream()
                .map(this::evaluateCase)
                .toList();
        List<CaseResult> failures = results.stream()
                .filter(result -> !result.passed())
                .toList();

        int total = results.size();
        int negativeCount = count(results, result -> !result.expectedCaptureAllowed());
        int positiveCount = count(results, CaseResult::expectedCaptureAllowed);
        int candidateTypeCount = count(results, result -> hasText(result.expectedMemoryType()));
        int correctionCount = count(results, result -> result.expectedCorrection() != null);
        int reasonCount = count(results, result -> hasText(result.expectedPolicyReason()));
        int signalCount = count(results, result -> !result.expectedSignals().isEmpty());

        return new EvaluationReport(
                total,
                negativeCount,
                positiveCount,
                ratio(count(results, CaseResult::decisionMatches), total),
                ratio(count(results, result -> !result.expectedCaptureAllowed() && !result.actualAllowed()),
                        negativeCount),
                ratio(count(results, result -> result.expectedCaptureAllowed() && result.actualAllowed()),
                        positiveCount),
                ratio(count(results, result -> hasText(result.expectedMemoryType())
                                && result.candidateTypeMatches()),
                        candidateTypeCount),
                ratio(count(results, result -> result.expectedCorrection() != null
                                && result.correctionMatches()),
                        correctionCount),
                ratio(count(results, result -> hasText(result.expectedPolicyReason())
                                && result.reasonMatches()),
                        reasonCount),
                ratio(count(results, result -> !result.expectedSignals().isEmpty()
                                && result.signalsMatch()),
                        signalCount),
                List.copyOf(failures),
                List.copyOf(results));
    }

    private CaseResult evaluateCase(MemoryReplayCase replayCase) {
        MemoryCapturePolicy.CaptureRequest request = new MemoryCapturePolicy.CaptureRequest(
                "user-1",
                replayCase.userMessage(),
                replayCase.assistantOutput());
        MemoryCapturePolicy.CaptureDecision decision = capturePolicy.evaluate(request);
        List<MemoryCandidateExtractor.MemoryCandidate> candidates = candidateExtractor.extract(request, decision);
        MemoryCandidateExtractor.MemoryCandidate firstCandidate = candidates.isEmpty() ? null : candidates.get(0);

        boolean actualAllowed = decision.allowed();
        String actualDecisionType = decision.type().name();
        String actualMemoryType = firstCandidate == null ? "" : firstCandidate.memoryType();
        Boolean actualCorrection = firstCandidate == null ? null : firstCandidate.correction();
        String actualPolicyReason = decision.reason();
        List<String> actualSignals = decision.matchedSignals();

        List<String> messages = new ArrayList<>();
        boolean decisionMatches = actualAllowed == replayCase.expectedCaptureAllowed()
                && matchesIfExpected(replayCase.expectedDecisionType(), actualDecisionType);
        if (actualAllowed != replayCase.expectedCaptureAllowed()) {
            messages.add("allowed expected=" + replayCase.expectedCaptureAllowed() + " actual=" + actualAllowed);
        }
        if (!matchesIfExpected(replayCase.expectedDecisionType(), actualDecisionType)) {
            messages.add("decisionType expected=" + replayCase.expectedDecisionType() + " actual=" + actualDecisionType);
        }

        boolean candidateTypeMatches = !hasText(replayCase.expectedMemoryType())
                || replayCase.expectedMemoryType().equals(actualMemoryType);
        if (!candidateTypeMatches) {
            messages.add("memoryType expected=" + replayCase.expectedMemoryType() + " actual=" + actualMemoryType);
        }

        boolean correctionMatches = replayCase.expectedCorrection() == null
                || replayCase.expectedCorrection().equals(actualCorrection);
        if (!correctionMatches) {
            messages.add("correction expected=" + replayCase.expectedCorrection() + " actual=" + actualCorrection);
        }

        boolean reasonMatches = !hasText(replayCase.expectedPolicyReason())
                || replayCase.expectedPolicyReason().equals(actualPolicyReason);
        if (!reasonMatches) {
            messages.add("policyReason expected=" + replayCase.expectedPolicyReason()
                    + " actual=" + actualPolicyReason);
        }

        boolean signalsMatch = replayCase.expectedSignals().stream().allMatch(actualSignals::contains);
        if (!signalsMatch) {
            messages.add("signals expectedToContain=" + replayCase.expectedSignals() + " actual=" + actualSignals);
        }

        return new CaseResult(
                replayCase.id(),
                messages.isEmpty(),
                List.copyOf(messages),
                replayCase.expectedCaptureAllowed(),
                actualAllowed,
                replayCase.expectedDecisionType(),
                actualDecisionType,
                replayCase.expectedMemoryType(),
                actualMemoryType,
                replayCase.expectedCorrection(),
                actualCorrection,
                replayCase.expectedPolicyReason(),
                actualPolicyReason,
                replayCase.expectedSignals(),
                actualSignals,
                decisionMatches,
                candidateTypeMatches,
                correctionMatches,
                reasonMatches,
                signalsMatch);
    }

    private static boolean matchesIfExpected(String expected, String actual) {
        return !hasText(expected) || expected.equals(actual);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static int count(List<CaseResult> results, ResultPredicate predicate) {
        int count = 0;
        for (CaseResult result : results) {
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
        boolean test(CaseResult result);
    }

    public record MemoryReplayCase(String id,
                                   String userMessage,
                                   String assistantOutput,
                                   boolean expectedCaptureAllowed,
                                   String expectedDecisionType,
                                   String expectedMemoryType,
                                   Boolean expectedCorrection,
                                   String expectedPolicyReason,
                                   List<String> expectedSignals) {
        public MemoryReplayCase {
            id = id == null ? "" : id;
            userMessage = userMessage == null ? "" : userMessage;
            assistantOutput = assistantOutput == null ? "" : assistantOutput;
            expectedDecisionType = expectedDecisionType == null ? "" : expectedDecisionType;
            expectedMemoryType = expectedMemoryType == null ? "" : expectedMemoryType;
            expectedPolicyReason = expectedPolicyReason == null ? "" : expectedPolicyReason;
            expectedSignals = expectedSignals == null ? List.of() : List.copyOf(expectedSignals);
        }
    }

    public record EvaluationReport(int totalCases,
                                   int shouldNotRememberCases,
                                   int shouldRememberCases,
                                   double decisionAccuracy,
                                   double shouldNotRememberPrecision,
                                   double shouldRememberRecall,
                                   double candidateTypeAccuracy,
                                   double correctionAccuracy,
                                   double reasonCoverage,
                                   double signalCoverage,
                                   List<CaseResult> failures,
                                   List<CaseResult> results) {
        public EvaluationReport {
            failures = failures == null ? List.of() : List.copyOf(failures);
            results = results == null ? List.of() : List.copyOf(results);
        }
    }

    public record CaseResult(String id,
                             boolean passed,
                             List<String> messages,
                             boolean expectedCaptureAllowed,
                             boolean actualAllowed,
                             String expectedDecisionType,
                             String actualDecisionType,
                             String expectedMemoryType,
                             String actualMemoryType,
                             Boolean expectedCorrection,
                             Boolean actualCorrection,
                             String expectedPolicyReason,
                             String actualPolicyReason,
                             List<String> expectedSignals,
                             List<String> actualSignals,
                             boolean decisionMatches,
                             boolean candidateTypeMatches,
                             boolean correctionMatches,
                             boolean reasonMatches,
                             boolean signalsMatch) {
        public CaseResult {
            messages = messages == null ? List.of() : List.copyOf(messages);
            expectedSignals = expectedSignals == null ? List.of() : List.copyOf(expectedSignals);
            actualSignals = actualSignals == null ? List.of() : List.copyOf(actualSignals);
        }
    }
}
