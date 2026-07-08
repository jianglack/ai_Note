package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryReplayDatasetLoaderTest {

    private static final int MIN_TOTAL_CASES = 2000;
    private static final int MIN_SIMULATED_CASES = 1800;
    private static final int MIN_CHINESE_CASES = 1300;
    private static final int MIN_ALLOW_CASES = 700;
    private static final int MIN_DENY_CASES = 700;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(?:\\+?\\d[\\d -]{8,}\\d)(?!\\d)");
    private static final Pattern REAL_DOMAIN_PATTERN = Pattern.compile(
            "(?i)\\b(?!example\\.com\\b|example\\.org\\b|example\\.net\\b)[a-z0-9-]+\\.(com|cn|net|org|io|ai)\\b");
    private static final Pattern AWS_KEY_PATTERN = Pattern.compile("AKIA[0-9A-Z]{16}");
    private static final List<String> REQUIRED_SCENARIO_TAGS = List.of(
            "chinese_stable_preference",
            "ambiguous_weak_signal",
            "multi_turn_correction",
            "misleading_rag",
            "selected_note_conflict",
            "complex_project_context",
            "operation_confirmation",
            "one_off_instruction",
            "assistant_feedback",
            "sensitive_pii_like",
            "forget_memory_control",
            "multilingual_code_switch");
    private static final Map<String, Integer> CATEGORY_MINIMUMS = categoryMinimums();

    @Test
    void loadsGovernedActiveDatasetWithRequiredProvenance() {
        MemoryReplayDataset dataset = MemoryReplayDatasetLoader.loadActiveDataset();
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases = dataset.cases();
        List<MemoryReplayDataset.ManifestEntry> manifest = dataset.manifest();

        assertThat(cases).hasSizeGreaterThanOrEqualTo(MIN_TOTAL_CASES);
        assertThat(manifest).hasSameSizeAs(cases);
        assertThat(cases)
                .extracting(MemoryReplayEvaluationService.MemoryReplayCase::id)
                .doesNotHaveDuplicates();
        assertThat(cases)
                .extracting(MemoryReplayEvaluationService.MemoryReplayCase::userMessage)
                .doesNotHaveDuplicates();

        Map<String, MemoryReplayDataset.ManifestEntry> manifestById = manifest.stream()
                .collect(Collectors.toMap(
                        MemoryReplayDataset.ManifestEntry::caseId,
                        entry -> entry,
                        (left, right) -> left,
                        LinkedHashMap::new));
        assertThat(manifestById).hasSize(cases.size());
        assertThat(cases).allSatisfy(replayCase -> assertThat(manifestById).containsKey(replayCase.id()));

        Map<String, Long> sourceCounts = manifest.stream()
                .collect(Collectors.groupingBy(
                        MemoryReplayDataset.ManifestEntry::sourceType,
                        LinkedHashMap::new,
                        Collectors.counting()));
        assertThat(sourceCounts.getOrDefault("synthetic_seed", 0L)).isGreaterThanOrEqualTo(200L);
        assertThat(sourceCounts.getOrDefault("simulated_realistic", 0L)).isGreaterThanOrEqualTo(MIN_SIMULATED_CASES);
        assertThat(sourceCounts).doesNotContainKey("real_user_anonymized");

        assertThat(manifest).allSatisfy(entry -> {
            assertThat(entry.caseId()).isNotBlank();
            assertThat(entry.sourceType()).isIn("synthetic_seed", "simulated_realistic");
            assertThat(entry.sourceReference()).isNotBlank();
            assertThat(entry.personaAgent()).isNotBlank();
            assertThat(entry.scenarioTags()).isNotEmpty();
            assertThat(entry.languageTags()).isNotEmpty();
            assertThat(entry.conversationId()).isNotBlank();
            assertThat(entry.redactionReportId()).isNotBlank();
            assertThat(entry.reviewStatus()).isEqualTo("approved");
            assertThat(entry.reviewer()).isNotBlank();
            assertThat(entry.approvedAt()).isNotBlank();
        });
    }

    @Test
    void activeDatasetCoversPersonasScenariosCategoriesAndDecisionBalance() {
        MemoryReplayDataset dataset = MemoryReplayDatasetLoader.loadActiveDataset();
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases = dataset.cases();
        List<MemoryReplayDataset.ManifestEntry> manifest = dataset.manifest();

        assertThat(cases.stream().filter(replayCase -> replayCase.id().startsWith("replay_cn_")).count())
                .isGreaterThanOrEqualTo(MIN_CHINESE_CASES);
        assertThat(cases.stream().filter(MemoryReplayEvaluationService.MemoryReplayCase::expectedCaptureAllowed).count())
                .isGreaterThanOrEqualTo(MIN_ALLOW_CASES);
        assertThat(cases.stream().filter(replayCase -> !replayCase.expectedCaptureAllowed()).count())
                .isGreaterThanOrEqualTo(MIN_DENY_CASES);

        Map<String, Long> categoryCounts = cases.stream()
                .collect(Collectors.groupingBy(
                        replayCase -> categoryOf(replayCase.id()),
                        LinkedHashMap::new,
                        Collectors.counting()));
        CATEGORY_MINIMUMS.forEach((category, minimum) ->
                assertThat(categoryCounts.getOrDefault(category, 0L))
                        .as(category)
                        .isGreaterThanOrEqualTo(minimum));

        Set<String> personas = manifest.stream()
                .map(MemoryReplayDataset.ManifestEntry::personaAgent)
                .collect(Collectors.toSet());
        assertThat(personas).hasSizeGreaterThanOrEqualTo(10);

        Set<String> scenarioTags = manifest.stream()
                .flatMap(entry -> entry.scenarioTags().stream())
                .collect(Collectors.toSet());
        assertThat(scenarioTags).containsAll(REQUIRED_SCENARIO_TAGS);
    }

    @Test
    void activeDatasetDoesNotContainCommittedPiiSecretsOrMojibake() {
        MemoryReplayDataset dataset = MemoryReplayDatasetLoader.loadActiveDataset();

        assertThat(dataset.cases()).allSatisfy(replayCase -> {
            String text = replayCase.userMessage() + " " + replayCase.assistantOutput();
            assertThat(text).as(replayCase.id()).doesNotContain("\uFFFD");
            assertThat(text).as(replayCase.id()).doesNotContain("sk-test123");
            assertThat(EMAIL_PATTERN.matcher(text).find()).as(replayCase.id() + " email").isFalse();
            assertThat(PHONE_PATTERN.matcher(text).find()).as(replayCase.id() + " phone").isFalse();
            assertThat(REAL_DOMAIN_PATTERN.matcher(text).find()).as(replayCase.id() + " domain").isFalse();
            assertThat(AWS_KEY_PATTERN.matcher(text).find()).as(replayCase.id() + " aws key").isFalse();
        });
    }

    private static Map<String, Integer> categoryMinimums() {
        Map<String, Integer> minimums = new LinkedHashMap<>();
        minimums.put("operation", 150);
        minimums.put("explicit_preference", 150);
        minimums.put("implicit_preference", 180);
        minimums.put("style", 180);
        minimums.put("correction", 140);
        minimums.put("project_context", 140);
        minimums.put("reference_only", 150);
        minimums.put("rag_reference", 150);
        minimums.put("one_off", 140);
        minimums.put("assistant_feedback", 100);
        minimums.put("sensitive", 100);
        minimums.put("forget", 100);
        minimums.put("ambiguous", 120);
        minimums.put("multi_turn_correction", 100);
        minimums.put("complex_project_context", 100);
        return minimums;
    }

    private static String categoryOf(String id) {
        String withoutLanguage = id == null ? "" : id.replaceFirst("^replay_(cn|en)_", "");
        for (String category : CATEGORY_MINIMUMS.keySet()) {
            if (withoutLanguage.startsWith(category + "_")) {
                return category;
            }
        }
        return "";
    }
}
