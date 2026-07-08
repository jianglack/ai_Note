package com.ainote.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class ExternalRealHumanReplayDatasetLoader {

    private static final String RESOURCE = "/memory/external-real-human-replay-dataset.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ExternalRealHumanReplayDatasetLoader() {
    }

    static MemoryReplayDataset load() {
        ExternalReplayResource resource = loadResource();
        MemoryReplayDataset dataset = new MemoryReplayDataset(resource.cases(), resource.manifest());
        assertNonEmpty(dataset);
        assertUnique(dataset.cases(), MemoryReplayEvaluationService.MemoryReplayCase::id, "duplicate external replay id");
        assertUnique(dataset.cases(), MemoryReplayEvaluationService.MemoryReplayCase::userMessage,
                "duplicate external replay userMessage");
        assertManifestMatchesCases(dataset);
        return dataset;
    }

    private static ExternalReplayResource loadResource() {
        try (InputStream input = ExternalRealHumanReplayDatasetLoader.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing external replay resource: " + RESOURCE);
            }
            return OBJECT_MAPPER.readValue(input, ExternalReplayResource.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load external replay resource: " + RESOURCE, e);
        }
    }

    private static void assertNonEmpty(MemoryReplayDataset dataset) {
        if (dataset.cases().isEmpty()) {
            throw new IllegalStateException("External replay dataset is empty");
        }
        if (dataset.cases().size() != dataset.manifest().size()) {
            throw new IllegalStateException("External replay manifest size does not match cases");
        }
    }

    private static void assertManifestMatchesCases(MemoryReplayDataset dataset) {
        Set<String> caseIds = dataset.cases().stream()
                .map(MemoryReplayEvaluationService.MemoryReplayCase::id)
                .collect(Collectors.toSet());
        Set<String> manifestIds = dataset.manifest().stream()
                .map(MemoryReplayDataset.ManifestEntry::caseId)
                .collect(Collectors.toSet());
        if (!caseIds.equals(manifestIds)) {
            throw new IllegalStateException("External replay manifest does not match cases");
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

    private record ExternalReplayResource(
            List<MemoryReplayEvaluationService.MemoryReplayCase> cases,
            List<MemoryReplayDataset.ManifestEntry> manifest) {
    }
}
