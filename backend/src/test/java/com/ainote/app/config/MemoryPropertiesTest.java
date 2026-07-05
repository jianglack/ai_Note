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
        assertThat(properties.getCapture().getAdvisor().isEnabled()).isFalse();
        assertThat(properties.getCapture().getAdvisor().getMinConfidence()).isEqualTo(0.82);
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
        values.put("app.memory.capture.advisor.enabled", "true");
        values.put("app.memory.capture.advisor.min-confidence", "0.9");
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
        assertThat(properties.getCapture().getAdvisor().isEnabled()).isTrue();
        assertThat(properties.getCapture().getAdvisor().getMinConfidence()).isEqualTo(0.9);
        assertThat(properties.getMaxPerUser()).isEqualTo(25);
        assertThat(properties.getSimilarityThreshold()).isEqualTo(0.91);
        assertThat(properties.getDecayHalfLifeDays()).isEqualTo(12.5);
    }
}
