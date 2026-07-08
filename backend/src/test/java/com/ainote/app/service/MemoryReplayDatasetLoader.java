package com.ainote.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class MemoryReplayDatasetLoader {

    private static final String SEED_RESOURCE = "/memory/replay-eval-cases.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MemoryReplayDatasetLoader() {
    }

    static MemoryReplayDataset loadActiveDataset() {
        List<MemoryReplayEvaluationService.MemoryReplayCase> seedCases = loadSeedCases();
        List<MemoryReplayDataset.ManifestEntry> seedManifest = seedCases.stream()
                .map(MemoryReplayDatasetLoader::seedManifestEntry)
                .toList();

        Map<String, Long> seedCategoryCounts = seedCases.stream()
                .collect(Collectors.groupingBy(
                        replayCase -> categoryOf(replayCase.id()),
                        LinkedHashMap::new,
                        Collectors.counting()));
        MemoryReplayDataset simulatedDataset = SimulatedMemoryReplayCaseGenerator.generate(seedCategoryCounts);

        List<MemoryReplayEvaluationService.MemoryReplayCase> activeCases = new ArrayList<>();
        activeCases.addAll(seedCases);
        activeCases.addAll(simulatedDataset.cases());

        List<MemoryReplayDataset.ManifestEntry> activeManifest = new ArrayList<>();
        activeManifest.addAll(seedManifest);
        activeManifest.addAll(simulatedDataset.manifest());

        assertUnique(activeCases, MemoryReplayEvaluationService.MemoryReplayCase::id, "duplicate replay id");
        assertUnique(activeCases, MemoryReplayEvaluationService.MemoryReplayCase::userMessage, "duplicate replay userMessage");
        assertManifestMatchesCases(activeCases, activeManifest);

        return new MemoryReplayDataset(activeCases, activeManifest);
    }

    private static List<MemoryReplayEvaluationService.MemoryReplayCase> loadSeedCases() {
        try (InputStream input = MemoryReplayDatasetLoader.class.getResourceAsStream(SEED_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing replay seed resource: " + SEED_RESOURCE);
            }
            return OBJECT_MAPPER.readValue(input, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load replay seed resource: " + SEED_RESOURCE, e);
        }
    }

    private static MemoryReplayDataset.ManifestEntry seedManifestEntry(
            MemoryReplayEvaluationService.MemoryReplayCase replayCase) {
        String language = replayCase.id().startsWith("replay_cn_") ? "zh" : "en";
        return new MemoryReplayDataset.ManifestEntry(
                replayCase.id(),
                "synthetic_seed",
                "resource:" + SEED_RESOURCE,
                "synthetic_seed_importer",
                List.of(scenarioTagForCategory(categoryOf(replayCase.id()))),
                List.of(language),
                "seed-" + replayCase.id(),
                "redaction-seed-v1",
                "approved",
                "seed_reviewer_v1",
                "2026-07-08T00:00:00Z");
    }

    static String categoryOf(String id) {
        String withoutLanguage = id == null ? "" : id.replaceFirst("^replay_(cn|en)_", "");
        for (String category : SimulatedMemoryReplayCaseGenerator.targetCategoryMinimums().keySet()) {
            if (withoutLanguage.startsWith(category + "_")) {
                return category;
            }
        }
        return "";
    }

    static String scenarioTagForCategory(String category) {
        return switch (category) {
            case "operation" -> "operation_confirmation";
            case "explicit_preference", "implicit_preference", "style" -> "chinese_stable_preference";
            case "correction", "multi_turn_correction" -> "multi_turn_correction";
            case "project_context", "complex_project_context" -> "complex_project_context";
            case "reference_only" -> "selected_note_conflict";
            case "rag_reference" -> "misleading_rag";
            case "one_off" -> "one_off_instruction";
            case "assistant_feedback" -> "assistant_feedback";
            case "sensitive" -> "sensitive_pii_like";
            case "forget" -> "forget_memory_control";
            case "ambiguous" -> "ambiguous_weak_signal";
            default -> "uncategorized";
        };
    }

    private static void assertManifestMatchesCases(
            List<MemoryReplayEvaluationService.MemoryReplayCase> cases,
            List<MemoryReplayDataset.ManifestEntry> manifest) {
        Set<String> caseIds = cases.stream()
                .map(MemoryReplayEvaluationService.MemoryReplayCase::id)
                .collect(Collectors.toSet());
        Set<String> manifestIds = manifest.stream()
                .map(MemoryReplayDataset.ManifestEntry::caseId)
                .collect(Collectors.toSet());
        if (!caseIds.equals(manifestIds)) {
            throw new IllegalStateException("Replay manifest does not match active cases");
        }
    }

    private static void assertUnique(List<MemoryReplayEvaluationService.MemoryReplayCase> cases,
                                     Function<MemoryReplayEvaluationService.MemoryReplayCase, String> valueExtractor,
                                     String message) {
        Set<String> values = cases.stream()
                .map(valueExtractor)
                .collect(Collectors.toSet());
        if (values.size() != cases.size()) {
            throw new IllegalStateException(message);
        }
    }
}
