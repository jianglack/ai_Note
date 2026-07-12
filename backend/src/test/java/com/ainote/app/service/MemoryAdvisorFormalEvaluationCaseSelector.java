package com.ainote.app.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class MemoryAdvisorFormalEvaluationCaseSelector {

    private MemoryAdvisorFormalEvaluationCaseSelector() {
    }

    static List<MemoryReplayEvaluationService.MemoryReplayCase> select(
            List<MemoryReplayEvaluationService.MemoryReplayCase> cases,
            SelectionConfig config) {
        List<MemoryReplayEvaluationService.MemoryReplayCase> safeCases =
                cases == null ? List.of() : List.copyOf(cases);
        SelectionConfig safeConfig = config == null ? new SelectionConfig("first", List.of(), 0) : config;
        if (!safeConfig.caseIds().isEmpty()) {
            return selectExactIds(safeCases, safeConfig.caseIds());
        }
        if ("stratified".equals(safeConfig.normalizedStrategy())) {
            return selectStratified(safeCases, safeConfig.maxCases());
        }
        return safeCases;
    }

    private static List<MemoryReplayEvaluationService.MemoryReplayCase> selectExactIds(
            List<MemoryReplayEvaluationService.MemoryReplayCase> cases,
            List<String> caseIds) {
        Map<String, MemoryReplayEvaluationService.MemoryReplayCase> byId = new LinkedHashMap<>();
        for (MemoryReplayEvaluationService.MemoryReplayCase replayCase : cases) {
            byId.put(replayCase.id(), replayCase);
        }

        List<String> missing = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<MemoryReplayEvaluationService.MemoryReplayCase> selected = new ArrayList<>();
        for (String caseId : caseIds) {
            if (!seen.add(caseId)) {
                continue;
            }
            MemoryReplayEvaluationService.MemoryReplayCase replayCase = byId.get(caseId);
            if (replayCase == null) {
                missing.add(caseId);
            } else {
                selected.add(replayCase);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Unknown memory advisor formal eval case ids: " + missing);
        }
        return List.copyOf(selected);
    }

    private static List<MemoryReplayEvaluationService.MemoryReplayCase> selectStratified(
            List<MemoryReplayEvaluationService.MemoryReplayCase> cases,
            int maxCases) {
        if (cases.isEmpty() || maxCases == 0) {
            return List.of();
        }
        if (maxCases < 0 || maxCases >= cases.size()) {
            return cases;
        }

        Map<String, List<MemoryReplayEvaluationService.MemoryReplayCase>> buckets = new LinkedHashMap<>();
        for (MemoryReplayEvaluationService.MemoryReplayCase replayCase : cases) {
            buckets.computeIfAbsent(bucketKey(replayCase.id()), ignored -> new ArrayList<>())
                    .add(replayCase);
        }
        List<Map.Entry<String, List<MemoryReplayEvaluationService.MemoryReplayCase>>> orderedBuckets =
                buckets.entrySet().stream()
                        .sorted(Comparator.comparing(Map.Entry::getKey))
                        .toList();

        List<MemoryReplayEvaluationService.MemoryReplayCase> selected = new ArrayList<>();
        int offset = 0;
        boolean added;
        do {
            added = false;
            for (Map.Entry<String, List<MemoryReplayEvaluationService.MemoryReplayCase>> bucket : orderedBuckets) {
                if (selected.size() >= maxCases) {
                    return List.copyOf(selected);
                }
                if (offset < bucket.getValue().size()) {
                    selected.add(bucket.getValue().get(offset));
                    added = true;
                }
            }
            offset++;
        } while (added && selected.size() < maxCases);
        return List.copyOf(selected);
    }

    private static String bucketKey(String caseId) {
        return categoryOf(caseId) + "|" + sourceOf(caseId);
    }

    private static String categoryOf(String caseId) {
        String id = caseId == null ? "" : caseId;
        String withoutLanguage = id.replaceFirst("^replay_(cn|en)_", "");
        if (withoutLanguage.contains("_external_")) {
            return withoutLanguage.substring(0, withoutLanguage.indexOf("_external_"));
        }
        if (withoutLanguage.contains("_sim_")) {
            return withoutLanguage.substring(0, withoutLanguage.indexOf("_sim_"));
        }
        int lastUnderscore = withoutLanguage.lastIndexOf('_');
        return lastUnderscore <= 0 ? withoutLanguage : withoutLanguage.substring(0, lastUnderscore);
    }

    private static String sourceOf(String caseId) {
        String id = caseId == null ? "" : caseId;
        if (id.contains("_external_")) {
            return "external";
        }
        if (id.contains("_sim_")) {
            return "simulated";
        }
        return "seed";
    }

    record SelectionConfig(String strategy, List<String> caseIds, int maxCases) {
        SelectionConfig {
            strategy = strategy == null ? "first" : strategy.trim().toLowerCase(Locale.ROOT);
            caseIds = caseIds == null
                    ? List.of()
                    : caseIds.stream()
                    .filter(caseId -> caseId != null && !caseId.isBlank())
                    .map(String::trim)
                    .toList();
        }

        String normalizedStrategy() {
            return strategy.isBlank() ? "first" : strategy;
        }

        boolean overridesDefaultCaseCount() {
            return !caseIds.isEmpty() || "stratified".equals(normalizedStrategy());
        }
    }
}
