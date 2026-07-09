package com.ainote.app.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class MemoryAdvisorPromptRegistry {

    public PromptMetadata current() {
        return metadataForV2();
    }

    public Optional<PromptMetadata> find(String promptVersion) {
        if (LlmMemorySignalAdvisor.PROMPT_VERSION.equals(promptVersion)) {
            return Optional.of(metadataForV2());
        }
        return Optional.empty();
    }

    public String hashCanonicalTemplate(String template) {
        String normalized = (template == null ? "" : template)
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[ \t]+(?=\n)", "")
                .trim();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private PromptMetadata metadataForV2() {
        List<String> memoryTypes = List.of("fact", "preference", "style", "project_context", "none");
        List<String> signals = List.of(
                "advisor_fact_signal",
                "advisor_preference_signal",
                "advisor_interaction_style_signal",
                "advisor_project_context_signal");
        return new PromptMetadata(
                LlmMemorySignalAdvisor.PROMPT_VERSION,
                "memory-advisor-json-v1",
                hashCanonicalTemplate(LlmMemorySignalAdvisor.canonicalPromptTemplate()),
                memoryTypes,
                signals,
                List.of("deepseek", "openai-compatible"),
                PromptStatus.CANDIDATE);
    }

    public enum PromptStatus {
        CANDIDATE,
        APPROVED,
        DEPRECATED
    }

    public record PromptMetadata(String promptVersion,
                                 String schemaVersion,
                                 String promptHash,
                                 List<String> allowedMemoryTypes,
                                 List<String> allowedSignals,
                                 List<String> compatibleModelFamilies,
                                 PromptStatus status) {
        public PromptMetadata {
            promptVersion = promptVersion == null ? "" : promptVersion;
            schemaVersion = schemaVersion == null ? "" : schemaVersion;
            promptHash = promptHash == null ? "" : promptHash;
            allowedMemoryTypes = allowedMemoryTypes == null ? List.of() : List.copyOf(allowedMemoryTypes);
            allowedSignals = allowedSignals == null ? List.of() : List.copyOf(allowedSignals);
            compatibleModelFamilies = compatibleModelFamilies == null ? List.of() : List.copyOf(compatibleModelFamilies);
            status = status == null ? PromptStatus.CANDIDATE : status;
        }
    }
}
