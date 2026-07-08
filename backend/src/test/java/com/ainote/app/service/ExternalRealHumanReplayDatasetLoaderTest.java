package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalRealHumanReplayDatasetLoaderTest {

    private static final int MIN_EXTERNAL_CASES = 2000;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(?:\\+?\\d[\\d -]{8,}\\d)(?!\\d)");
    private static final Pattern REAL_DOMAIN_PATTERN = Pattern.compile(
            "(?i)\\b(?!example\\.com\\b|example\\.org\\b|example\\.net\\b)[a-z0-9-]+\\.(com|cn|net|org|io|ai)\\b");
    private static final Pattern AWS_KEY_PATTERN = Pattern.compile("AKIA[0-9A-Z]{16}");
    private static final List<String> REQUIRED_PROJECT_RELEVANCE_TAGS = List.of(
            "external_memory_preference",
            "external_style_request",
            "external_correction",
            "external_work_context",
            "external_reference_task",
            "external_one_off",
            "external_assistant_feedback",
            "external_memory_control",
            "external_sensitive_boundary",
            "external_ambiguous");
    private static final Map<String, Integer> MIN_PROJECT_RELEVANCE_COUNTS = Map.of(
            "external_memory_preference", 1000,
            "external_work_context", 400,
            "external_style_request", 300,
            "external_reference_task", 200,
            "external_correction", 50,
            "external_sensitive_boundary", 10,
            "external_memory_control", 4,
            "external_one_off", 5,
            "external_assistant_feedback", 2,
            "external_ambiguous", 1000);

    @Test
    void loadsApprovedExternalRealHumanReplayDatasetWithProvenance() {
        MemoryReplayDataset dataset = ExternalRealHumanReplayDatasetLoader.load();

        assertThat(dataset.cases()).hasSizeGreaterThanOrEqualTo(MIN_EXTERNAL_CASES);
        assertThat(dataset.manifest()).hasSameSizeAs(dataset.cases());
        assertThat(dataset.cases())
                .extracting(MemoryReplayEvaluationService.MemoryReplayCase::id)
                .doesNotHaveDuplicates();
        assertThat(dataset.cases())
                .extracting(MemoryReplayEvaluationService.MemoryReplayCase::userMessage)
                .doesNotHaveDuplicates();
        assertThat(dataset.manifest())
                .extracting(MemoryReplayDataset.ManifestEntry::sourceReference)
                .doesNotHaveDuplicates();

        Map<String, MemoryReplayDataset.ManifestEntry> manifestById = dataset.manifest().stream()
                .collect(Collectors.toMap(
                        MemoryReplayDataset.ManifestEntry::caseId,
                        entry -> entry,
                        (left, right) -> left,
                        LinkedHashMap::new));

        assertThat(dataset.cases()).allSatisfy(replayCase -> assertThat(manifestById)
                .as(replayCase.id())
                .containsKey(replayCase.id()));
        assertThat(dataset.manifest()).allSatisfy(entry -> {
            assertThat(entry.sourceType()).isEqualTo("external_real_human_conversation");
            assertThat(entry.sourceReference()).contains("hf://datasets/allenai/WildChat/default/train");
            assertThat(entry.personaAgent()).isEqualTo("external_human_user");
            assertThat(entry.scenarioTags()).contains("external_real_human_conversation");
            assertThat(entry.languageTags()).isNotEmpty();
            assertThat(entry.conversationId()).isNotBlank();
            assertThat(entry.redactionReportId()).isEqualTo("redaction-external-wildchat-v1");
            assertThat(entry.reviewStatus()).isEqualTo("approved");
            assertThat(entry.reviewer()).isEqualTo("external_replay_curator_v1");
            assertThat(entry.approvedAt()).isEqualTo("2026-07-08T00:00:00Z");
        });
    }

    @Test
    void externalDatasetIsFilteredForProjectRelevantMemoryScenarios() {
        MemoryReplayDataset dataset = ExternalRealHumanReplayDatasetLoader.load();

        Map<String, Long> scenarioCounts = dataset.manifest().stream()
                .flatMap(entry -> entry.scenarioTags().stream())
                .collect(Collectors.groupingBy(
                        tag -> tag,
                        LinkedHashMap::new,
                        Collectors.counting()));
        assertThat(scenarioCounts.keySet()).containsAll(REQUIRED_PROJECT_RELEVANCE_TAGS);
        MIN_PROJECT_RELEVANCE_COUNTS.forEach((tag, minimum) -> assertThat(scenarioCounts.getOrDefault(tag, 0L))
                .as(tag)
                .isGreaterThanOrEqualTo(minimum));

        Map<String, Long> categoryCounts = dataset.cases().stream()
                .collect(Collectors.groupingBy(
                        replayCase -> MemoryReplayDatasetLoader.categoryOf(replayCase.id()),
                        LinkedHashMap::new,
                        Collectors.counting()));
        assertThat(categoryCounts.keySet()).containsAnyOf(
                "explicit_preference",
                "implicit_preference",
                "style",
                "correction",
                "reference_only",
                "one_off",
                "assistant_feedback",
                "forget",
                "ambiguous");
    }

    @Test
    void externalDatasetDoesNotContainCommittedPiiSecretsOrMojibake() {
        MemoryReplayDataset dataset = ExternalRealHumanReplayDatasetLoader.load();

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
}
