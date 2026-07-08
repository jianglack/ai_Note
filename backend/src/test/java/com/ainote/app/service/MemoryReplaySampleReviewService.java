package com.ainote.app.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class MemoryReplaySampleReviewService {

    private static final int REAL_USER_PILOT_MINIMUM = 50;
    private static final Set<String> SUPPORTED_SOURCE_TYPES = Set.of(
            "synthetic_seed",
            "simulated_realistic",
            "real_user_anonymized");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<!\\d)(?:\\+?\\d[\\d -]{8,}\\d)(?!\\d)");
    private static final Pattern REAL_DOMAIN_PATTERN = Pattern.compile(
            "(?i)\\b(?!example\\.com\\b|example\\.org\\b|example\\.net\\b)[a-z0-9-]+\\.(com|cn|net|org|io|ai)\\b");
    private static final Pattern AWS_KEY_PATTERN = Pattern.compile("AKIA[0-9A-Z]{16}");

    private MemoryReplaySampleReviewService() {
    }

    static SampleReviewReport review(MemoryReplayDataset dataset) {
        MemoryReplayDataset safeDataset = dataset == null ? new MemoryReplayDataset(List.of(), List.of()) : dataset;
        Map<String, MemoryReplayDataset.ManifestEntry> manifestByCaseId = safeDataset.manifest().stream()
                .collect(Collectors.toMap(
                        MemoryReplayDataset.ManifestEntry::caseId,
                        entry -> entry,
                        (left, right) -> left,
                        LinkedHashMap::new));

        List<SampleReviewRecord> records = safeDataset.cases().stream()
                .map(replayCase -> reviewCase(replayCase, manifestByCaseId.get(replayCase.id())))
                .toList();

        Map<String, Long> sourceCounts = records.stream()
                .collect(Collectors.groupingBy(
                        SampleReviewRecord::sourceType,
                        LinkedHashMap::new,
                        Collectors.counting()));
        long approvedActiveEvaluationCases = records.stream()
                .filter(SampleReviewRecord::approvedForActiveEvaluation)
                .count();
        long quarantinedCases = records.stream()
                .filter(record -> "quarantine".equals(record.intakeBucket()))
                .count();
        long realUserApprovedCases = records.stream()
                .filter(SampleReviewRecord::eligibleAsRealUserSample)
                .count();

        List<String> gateFailures = new ArrayList<>();
        boolean realUserPilotReady = realUserApprovedCases >= REAL_USER_PILOT_MINIMUM;
        if (!realUserPilotReady) {
            gateFailures.add("real_user_pilot_requires_50_approved_samples");
        }

        return new SampleReviewReport(
                safeDataset.cases().size(),
                approvedActiveEvaluationCases,
                quarantinedCases,
                realUserApprovedCases,
                realUserPilotReady,
                sourceCounts,
                records,
                gateFailures);
    }

    private static SampleReviewRecord reviewCase(
            MemoryReplayEvaluationService.MemoryReplayCase replayCase,
            MemoryReplayDataset.ManifestEntry manifestEntry) {
        List<String> flags = new ArrayList<>();
        String sourceType = manifestEntry == null ? "" : manifestEntry.sourceType();
        if (manifestEntry == null) {
            flags.add("missing_manifest");
        } else {
            validateManifest(manifestEntry, flags);
        }
        validateContent(replayCase, flags);

        boolean simulationSource = "synthetic_seed".equals(sourceType) || "simulated_realistic".equals(sourceType);
        boolean realUserSource = "real_user_anonymized".equals(sourceType);
        boolean approved = flags.isEmpty();

        String intakeBucket;
        if (approved && simulationSource) {
            intakeBucket = "simulation_baseline";
        } else if (approved && realUserSource) {
            intakeBucket = "real_user_candidate";
        } else {
            intakeBucket = "quarantine";
        }

        return new SampleReviewRecord(
                replayCase.id(),
                sourceType,
                intakeBucket,
                approved && (simulationSource || realUserSource),
                approved && realUserSource,
                flags);
    }

    private static void validateManifest(MemoryReplayDataset.ManifestEntry entry, List<String> flags) {
        if (!SUPPORTED_SOURCE_TYPES.contains(entry.sourceType())) {
            flags.add("unsupported_source_type");
        }
        if (isBlank(entry.sourceReference())) {
            flags.add("missing_source_reference");
        }
        if (isBlank(entry.redactionReportId())) {
            flags.add("missing_redaction_report_id");
        }
        if (isBlank(entry.reviewer())) {
            flags.add("missing_reviewer");
        }
        if (isBlank(entry.approvedAt())) {
            flags.add("missing_approved_at");
        }
        if (entry.scenarioTags().isEmpty()) {
            flags.add("missing_scenario_tags");
        }
        if (entry.languageTags().isEmpty()) {
            flags.add("missing_language_tags");
        }
        if (!"approved".equals(entry.reviewStatus())) {
            flags.add("review_not_approved");
        }
    }

    private static void validateContent(MemoryReplayEvaluationService.MemoryReplayCase replayCase,
                                        List<String> flags) {
        String text = replayCase.userMessage() + " " + replayCase.assistantOutput();
        if (text.contains("\uFFFD")) {
            flags.add("contains_replacement_character");
        }
        if (text.contains("sk-test123")) {
            flags.add("contains_known_test_secret");
        }
        if (EMAIL_PATTERN.matcher(text).find()) {
            flags.add("contains_email");
        }
        if (PHONE_PATTERN.matcher(text).find()) {
            flags.add("contains_phone");
        }
        if (REAL_DOMAIN_PATTERN.matcher(text).find()) {
            flags.add("contains_real_domain");
        }
        if (AWS_KEY_PATTERN.matcher(text).find()) {
            flags.add("contains_aws_key");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    record SampleReviewReport(
            int totalCases,
            long approvedActiveEvaluationCases,
            long quarantinedCases,
            long realUserApprovedCases,
            boolean realUserPilotReady,
            Map<String, Long> sourceCounts,
            List<SampleReviewRecord> records,
            List<String> gateFailures) {

        SampleReviewReport {
            sourceCounts = sourceCounts == null ? Map.of() : Map.copyOf(sourceCounts);
            records = records == null ? List.of() : List.copyOf(records);
            gateFailures = gateFailures == null ? List.of() : List.copyOf(gateFailures);
        }
    }

    record SampleReviewRecord(
            String caseId,
            String sourceType,
            String intakeBucket,
            boolean approvedForActiveEvaluation,
            boolean eligibleAsRealUserSample,
            List<String> flags) {

        SampleReviewRecord {
            caseId = caseId == null ? "" : caseId;
            sourceType = sourceType == null ? "" : sourceType;
            intakeBucket = intakeBucket == null ? "" : intakeBucket;
            flags = flags == null ? List.of() : List.copyOf(flags);
        }
    }
}
