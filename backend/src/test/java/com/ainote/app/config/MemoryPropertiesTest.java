package com.ainote.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryPropertiesTest {

    @Test
    void defaultsKeepGovernedBehaviorBehindFeatureFlags() {
        MemoryProperties properties = new MemoryProperties();

        assertThat(properties.getOrchestrator().isEnabled()).isFalse();
        assertThat(properties.getCapture().isEnabled()).isTrue();
        assertThat(properties.getCapture().getMode()).isEqualTo(MemoryProperties.CaptureMode.LEGACY);
        assertThat(properties.getRetrieval().getMode()).isEqualTo(MemoryProperties.RetrievalMode.LEGACY);
        assertThat(properties.getAudit().isEnabled()).isTrue();
        assertThat(properties.getChatHistory().getWriteMode()).isEqualTo(MemoryProperties.ChatHistoryWriteMode.APPEND);
        assertThat(properties.getChatHistory().getModelWindowMaxMessages()).isEqualTo(256);
        assertThat(properties.getChatHistory().getFlushMaxAttempts()).isEqualTo(3);
        assertThat(properties.getChatHistory().isCompactionEnabled()).isTrue();
        assertThat(properties.getChatHistory().getCompactionMaxAttempts()).isEqualTo(5);
        assertThat(properties.getChatHistory().getCompactionMaxSourceCharacters()).isEqualTo(12000);
        assertThat(properties.getCapture().getAdvisor().isEnabled()).isFalse();
        assertThat(properties.getCapture().getAdvisor().getMinConfidence()).isEqualTo(0.80);
        assertThat(properties.getPrivacy().isRedactExports()).isTrue();
        assertThat(properties.getPrivacy().getExportMaxItems()).isEqualTo(1000);
        assertThat(properties.getPrivacy().getDeletedMemoryRetentionDays()).isEqualTo(30);
        assertThat(properties.getPrivacy().isAtRestEncryptionRequired()).isTrue();
        assertThat(properties.getPrivacy().isAtRestEncryptionConfirmed()).isFalse();
        assertThat(properties.getPrivacy().getAtRestEncryptionKeyRef()).isEmpty();
        assertThat(properties.getMaxPerUser()).isEqualTo(50);
        assertThat(properties.getSimilarityThreshold()).isEqualTo(0.85);
        assertThat(properties.getDecayHalfLifeDays()).isEqualTo(30.0);
    }

    @Test
    void bindsNestedMemoryFeatureFlags() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("app.memory.orchestrator.enabled", "true");
        values.put("app.memory.capture.enabled", "false");
        values.put("app.memory.capture.mode", "policy");
        values.put("app.memory.retrieval.mode", "query_relevant");
        values.put("app.memory.audit.enabled", "false");
        values.put("app.memory.chat-history.write-mode", "legacy_rewrite");
        values.put("app.memory.chat-history.model-window-max-messages", "128");
        values.put("app.memory.chat-history.flush-max-attempts", "4");
        values.put("app.memory.chat-history.compaction-enabled", "false");
        values.put("app.memory.chat-history.compaction-max-attempts", "7");
        values.put("app.memory.capture.advisor.enabled", "true");
        values.put("app.memory.capture.advisor.min-confidence", "0.9");
        values.put("app.memory.privacy.redact-exports", "false");
        values.put("app.memory.privacy.export-max-items", "250");
        values.put("app.memory.privacy.deleted-memory-retention-days", "45");
        values.put("app.memory.privacy.at-rest-encryption-required", "true");
        values.put("app.memory.privacy.at-rest-encryption-confirmed", "true");
        values.put("app.memory.privacy.at-rest-encryption-key-ref", "kms/prod/memory");
        values.put("app.memory.max-per-user", "25");
        values.put("app.memory.similarity-threshold", "0.91");
        values.put("app.memory.decay-half-life-days", "12.5");

        MemoryProperties properties = new Binder(new MapConfigurationPropertySource(values))
                .bind("app.memory", Bindable.of(MemoryProperties.class))
                .get();

        assertThat(properties.getOrchestrator().isEnabled()).isTrue();
        assertThat(properties.getCapture().isEnabled()).isFalse();
        assertThat(properties.getCapture().getMode()).isEqualTo(MemoryProperties.CaptureMode.POLICY);
        assertThat(properties.getRetrieval().getMode()).isEqualTo(MemoryProperties.RetrievalMode.QUERY_RELEVANT);
        assertThat(properties.getAudit().isEnabled()).isFalse();
        assertThat(properties.getChatHistory().getWriteMode()).isEqualTo(MemoryProperties.ChatHistoryWriteMode.LEGACY_REWRITE);
        assertThat(properties.getChatHistory().getModelWindowMaxMessages()).isEqualTo(128);
        assertThat(properties.getChatHistory().getFlushMaxAttempts()).isEqualTo(4);
        assertThat(properties.getChatHistory().isCompactionEnabled()).isFalse();
        assertThat(properties.getChatHistory().getCompactionMaxAttempts()).isEqualTo(7);
        assertThat(properties.getCapture().getAdvisor().isEnabled()).isTrue();
        assertThat(properties.getCapture().getAdvisor().getMinConfidence()).isEqualTo(0.9);
        assertThat(properties.getPrivacy().isRedactExports()).isFalse();
        assertThat(properties.getPrivacy().getExportMaxItems()).isEqualTo(250);
        assertThat(properties.getPrivacy().getDeletedMemoryRetentionDays()).isEqualTo(45);
        assertThat(properties.getPrivacy().isAtRestEncryptionRequired()).isTrue();
        assertThat(properties.getPrivacy().isAtRestEncryptionConfirmed()).isTrue();
        assertThat(properties.getPrivacy().getAtRestEncryptionKeyRef()).isEqualTo("kms/prod/memory");
        assertThat(properties.getMaxPerUser()).isEqualTo(25);
        assertThat(properties.getSimilarityThreshold()).isEqualTo(0.91);
        assertThat(properties.getDecayHalfLifeDays()).isEqualTo(12.5);
    }
}
