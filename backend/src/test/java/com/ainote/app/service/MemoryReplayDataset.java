package com.ainote.app.service;

import java.util.List;

public record MemoryReplayDataset(
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases,
        List<ManifestEntry> manifest) {

    public MemoryReplayDataset {
        cases = cases == null ? List.of() : List.copyOf(cases);
        manifest = manifest == null ? List.of() : List.copyOf(manifest);
    }

    public record ManifestEntry(
            String caseId,
            String sourceType,
            String sourceReference,
            String personaAgent,
            List<String> scenarioTags,
            List<String> languageTags,
            String conversationId,
            String redactionReportId,
            String reviewStatus,
            String reviewer,
            String approvedAt) {

        public ManifestEntry {
            caseId = normalize(caseId);
            sourceType = normalize(sourceType);
            sourceReference = normalize(sourceReference);
            personaAgent = normalize(personaAgent);
            scenarioTags = scenarioTags == null ? List.of() : List.copyOf(scenarioTags);
            languageTags = languageTags == null ? List.of() : List.copyOf(languageTags);
            conversationId = normalize(conversationId);
            redactionReportId = normalize(redactionReportId);
            reviewStatus = normalize(reviewStatus);
            reviewer = normalize(reviewer);
            approvedAt = normalize(approvedAt);
        }

        private static String normalize(String value) {
            return value == null ? "" : value;
        }
    }
}
