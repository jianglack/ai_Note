package com.ainote.app.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MemoryAdvisorAbComparisonService {

    private final MemoryCapturePolicy policy;
    private final MemoryCandidateExtractor candidateExtractor;

    public MemoryAdvisorAbComparisonService(MemoryCapturePolicy policy,
                                            MemoryCandidateExtractor candidateExtractor) {
        this.policy = policy == null ? new MemoryCapturePolicy() : policy;
        this.candidateExtractor = candidateExtractor == null ? new MemoryCandidateExtractor() : candidateExtractor;
    }

    public AbComparisonReport compare(List<MemoryReplayEvaluationService.MemoryReplayCase> cases,
                                      List<MemoryAdvisorFormalBatchEvaluationService.ProgressEntry> progress) {
        List<MemoryReplayEvaluationService.MemoryReplayCase> safeCases = cases == null ? List.of() : cases;
        Map<String, MemoryAdvisorFormalBatchEvaluationService.ProgressEntry> progressByCaseId =
                progressByCaseId(progress);
        int bothAllow = 0;
        int bothDeny = 0;
        int advisorOnlyAllow = 0;
        int ruleOnlyAllow = 0;
        int advisorCorrect = 0;
        int ruleCorrect = 0;
        List<AbCaseSample> regressions = new java.util.ArrayList<>();
        List<AbCaseSample> disagreements = new java.util.ArrayList<>();

        for (MemoryReplayEvaluationService.MemoryReplayCase replayCase : safeCases) {
            MemoryAdvisorFormalBatchEvaluationService.ProgressEntry entry = progressByCaseId.get(replayCase.id());
            MemoryCapturePolicy.CaptureDecision ruleDecision = policy.evaluate(new MemoryCapturePolicy.CaptureRequest(
                    "user-1",
                    replayCase.userMessage(),
                    replayCase.assistantOutput()));
            boolean ruleAllowed = ruleDecision.allowed();
            boolean advisorAllowed = entry != null && entry.available() && entry.shouldCapture();
            boolean expectedAllowed = replayCase.expectedCaptureAllowed();
            if (ruleAllowed && advisorAllowed) {
                bothAllow++;
            } else if (!ruleAllowed && !advisorAllowed) {
                bothDeny++;
            } else if (advisorAllowed) {
                advisorOnlyAllow++;
            } else {
                ruleOnlyAllow++;
            }
            if (advisorAllowed == expectedAllowed) {
                advisorCorrect++;
            }
            if (ruleAllowed == expectedAllowed) {
                ruleCorrect++;
            }
            AbCaseSample sample = new AbCaseSample(
                    replayCase.id(),
                    replayCase.userMessage(),
                    expectedAllowed,
                    ruleAllowed,
                    advisorAllowed,
                    ruleDecision.type().name(),
                    entry == null ? "missing_progress" : entry.memoryType(),
                    entry == null ? "missing progress" : entry.reason());
            if (ruleAllowed != advisorAllowed) {
                disagreements.add(sample);
            }
            if (ruleAllowed == expectedAllowed && advisorAllowed != expectedAllowed) {
                regressions.add(sample);
            }
        }

        int totalCases = safeCases.size();
        return new AbComparisonReport(
                totalCases,
                bothAllow,
                bothDeny,
                advisorOnlyAllow,
                ruleOnlyAllow,
                ratio(advisorCorrect, totalCases),
                ratio(ruleCorrect, totalCases),
                ratio(advisorCorrect, totalCases) - ratio(ruleCorrect, totalCases),
                regressions,
                disagreements);
    }

    private Map<String, MemoryAdvisorFormalBatchEvaluationService.ProgressEntry> progressByCaseId(
            List<MemoryAdvisorFormalBatchEvaluationService.ProgressEntry> progress) {
        Map<String, MemoryAdvisorFormalBatchEvaluationService.ProgressEntry> progressByCaseId = new LinkedHashMap<>();
        if (progress == null) {
            return progressByCaseId;
        }
        for (MemoryAdvisorFormalBatchEvaluationService.ProgressEntry entry : progress) {
            if (entry != null) {
                progressByCaseId.put(entry.caseId(), entry);
            }
        }
        return progressByCaseId;
    }

    private static double ratio(int numerator, int denominator) {
        return denominator <= 0 ? 0.0 : (double) numerator / denominator;
    }

    public record AbComparisonReport(int totalCases,
                                     int bothAllow,
                                     int bothDeny,
                                     int advisorOnlyAllow,
                                     int ruleOnlyAllow,
                                     double advisorDecisionAccuracy,
                                     double ruleDecisionAccuracy,
                                     double advisorAccuracyDeltaVsRule,
                                     List<AbCaseSample> advisorRegressions,
                                     List<AbCaseSample> disagreements) {
        public AbComparisonReport {
            totalCases = Math.max(0, totalCases);
            bothAllow = Math.max(0, bothAllow);
            bothDeny = Math.max(0, bothDeny);
            advisorOnlyAllow = Math.max(0, advisorOnlyAllow);
            ruleOnlyAllow = Math.max(0, ruleOnlyAllow);
            advisorRegressions = advisorRegressions == null ? List.of() : List.copyOf(advisorRegressions);
            disagreements = disagreements == null ? List.of() : List.copyOf(disagreements);
        }
    }

    public record AbCaseSample(String caseId,
                               String userMessage,
                               boolean expectedAllowed,
                               boolean ruleAllowed,
                               boolean advisorAllowed,
                               String ruleDecisionType,
                               String advisorMemoryType,
                               String advisorReason) {
        public AbCaseSample {
            caseId = caseId == null ? "" : caseId;
            userMessage = userMessage == null ? "" : userMessage;
            ruleDecisionType = ruleDecisionType == null ? "" : ruleDecisionType;
            advisorMemoryType = advisorMemoryType == null ? "" : advisorMemoryType;
            advisorReason = advisorReason == null ? "" : advisorReason;
        }
    }
}
