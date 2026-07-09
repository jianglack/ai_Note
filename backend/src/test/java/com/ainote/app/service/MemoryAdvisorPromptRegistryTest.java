package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryAdvisorPromptRegistryTest {

    private final MemoryAdvisorPromptRegistry registry = new MemoryAdvisorPromptRegistry();

    @Test
    void currentPromptMetadataMatchesV2AndUsesSha256Hash() {
        MemoryAdvisorPromptRegistry.PromptMetadata metadata = registry.current();

        assertThat(metadata.promptVersion()).isEqualTo("memory-advisor-v2");
        assertThat(metadata.status()).isEqualTo(MemoryAdvisorPromptRegistry.PromptStatus.CANDIDATE);
        assertThat(metadata.schemaVersion()).isEqualTo("memory-advisor-json-v1");
        assertThat(metadata.promptHash()).matches("[a-f0-9]{64}");
        assertThat(metadata.allowedMemoryTypes()).containsExactlyInAnyOrder(
                "fact", "preference", "style", "project_context", "none");
        assertThat(metadata.allowedSignals()).containsExactlyInAnyOrder(
                "advisor_fact_signal",
                "advisor_preference_signal",
                "advisor_interaction_style_signal",
                "advisor_project_context_signal");
        assertThat(metadata.compatibleModelFamilies()).contains("deepseek");
    }

    @Test
    void unknownPromptVersionIsNotRegistered() {
        assertThat(registry.find("memory-advisor-v1")).isEmpty();
    }

    @Test
    void canonicalHashChangesWhenTemplateChanges() {
        String originalHash = registry.hashCanonicalTemplate("system\nuser\nschema");
        String changedHash = registry.hashCanonicalTemplate("system\nuser\nschema changed");

        assertThat(originalHash).matches("[a-f0-9]{64}");
        assertThat(changedHash).matches("[a-f0-9]{64}");
        assertThat(changedHash).isNotEqualTo(originalHash);
    }
}
